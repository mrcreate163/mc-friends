package com.example.mcfriends.service;

import com.example.mcfriends.client.AccountClient;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.exception.SelfFriendshipException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.repository.FriendshipRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final KafkaProducerService kafkaProducerService;
    private final AccountClient accountClient;

    public FriendshipService(
            FriendshipRepository friendshipRepository,
            KafkaProducerService kafkaProducerService,
            AccountClient accountClient
    ) {
        this.friendshipRepository = friendshipRepository;
        this.kafkaProducerService = kafkaProducerService;
        this.accountClient = accountClient;
    }

    public Friendship sendFriendRequest(UUID initiatorId, UUID targetId) {
        // Validate: Cannot send friend request to yourself
        if (initiatorId.equals(targetId)) {
            throw new SelfFriendshipException();
        }

        // Check if friendship already exists
        friendshipRepository.findByUserIds(initiatorId, targetId).ifPresent(friendship -> {
            switch (friendship.getStatus()) {
                case PENDING -> throw new FriendshipAlreadyExistsException("Friend request already pending");
                case ACCEPTED -> throw new FriendshipAlreadyExistsException("Users are already friends");
                case BLOCKED -> throw new FriendshipAlreadyExistsException("Cannot send friend request: user is blocked");
            }
        });

        Friendship request = new Friendship();
        request.setUserIdInitiator(initiatorId);
        request.setUserIdTarget(targetId);
        request.setStatus(FriendshipStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        return friendshipRepository.save(request);
    }

    public Friendship acceptFriendRequest(UUID requestId, UUID currentUserId) {
        Friendship request = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Запрос на дружбу с ID " + requestId + " не найден"));

        if (!request.getUserIdTarget().equals(currentUserId)) {

            throw new RuntimeException("Доступ запрещен: Только целевой пользователь может принять этот запрос.");
        }

        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new RuntimeException("Запрос уже обработан");
        }

        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setUpdatedAt(LocalDateTime.now());
        Friendship accepted = friendshipRepository.save(request);

        UUID userToNotify = accepted.getUserIdInitiator();
        kafkaProducerService.sendNotification(userToNotify, "Ваш запрос в друзья был принят!");

        return accepted;
    }

    public List<Friendship> getAcceptedFriends(UUID userId) {
        return friendshipRepository.findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                userId, FriendshipStatus.ACCEPTED,
                userId, FriendshipStatus.ACCEPTED
        );
    }

    private Optional<Friendship> findFriendshipBetweenUsers(UUID userId1, UUID userId2) {
        Optional<Friendship> friendship =
                friendshipRepository.findByUserIdInitiatorAndUserIdTarget(userId1, userId2);

        if (friendship.isEmpty()) {
            friendship = friendshipRepository.findByUserIdInitiatorAndUserIdTarget(userId2, userId1);
        }
        return friendship;
    }

    public void deleteFriendship(UUID currentUserId, UUID friendId) {
        Friendship friendship = findFriendshipBetweenUsers(currentUserId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Связь с пользователем " + friendId + " не найдена."));
        friendshipRepository.delete(friendship);
    }

    public List<FriendDto> getAcceptedFriendsDetails(UUID userId) {
        List<Friendship> friendships =
                friendshipRepository.findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                        userId, FriendshipStatus.ACCEPTED,
                        userId, FriendshipStatus.ACCEPTED
                );

        Set<UUID> friendIds = friendships.stream()
                .map(f -> f.getUserIdInitiator().equals(userId)
                        ? f.getUserIdTarget()
                        : f.getUserIdInitiator())
                .collect(Collectors.toSet());

        if (friendIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, AccountDto> accountMap = accountClient
                .getAccountsByIds(new ArrayList<>(friendIds))
                .stream()
                .collect(Collectors.toMap(AccountDto::getId, a -> a));

        return friendships.stream()
                .map(f -> {
                    UUID friendId = f.getUserIdInitiator().equals(userId)
                            ? f.getUserIdTarget()
                            : f.getUserIdInitiator();

                    AccountDto account = accountMap.get(friendId);
                    if (account == null) {
                        return null;
                    }

                    FriendDto dto = new FriendDto();
                    dto.setAccount(account);
                    dto.setStatus(f.getStatus());
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
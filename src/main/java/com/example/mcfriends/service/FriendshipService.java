package com.example.mcfriends.service;

import com.example.mcfriends.client.AccountClient;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.IncomingFriendRequestDto;
import com.example.mcfriends.dto.NotificationEvent;
import com.example.mcfriends.exception.BadRequestException;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.repository.FriendshipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Friendship declineFriendRequest(UUID requestId, UUID currentUserId) {
        Friendship request = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Запрос на дружбу с ID " + requestId + " не найден"));

        if (!request.getUserIdTarget().equals(currentUserId)) {
            throw new ForbiddenException("Доступ запрещен: Только целевой пользователь может отклонить этот запрос.");
        }

        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new BadRequestException("Запрос уже обработан");
        }

        request.setStatus(FriendshipStatus.DECLINED);
        request.setUpdatedAt(LocalDateTime.now());
        Friendship declined = friendshipRepository.save(request);

        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_REQUEST_DECLINED");
        event.setRecipientId(declined.getUserIdInitiator());
        event.setSenderId(currentUserId);
        kafkaProducerService.sendAccountChangesEvent(event);

        return declined;
    }

    public Page<IncomingFriendRequestDto> getIncomingFriendRequests(UUID currentUserId, Pageable pageable) {
        Page<Friendship> requests = friendshipRepository.findByUserIdTargetAndStatus(
                currentUserId, FriendshipStatus.PENDING, pageable);

        return requests.map(friendship -> {
            IncomingFriendRequestDto dto = new IncomingFriendRequestDto();
            dto.setRequestId(friendship.getId());
            dto.setCreatedAt(friendship.getCreatedAt());
            
            try {
                AccountDto initiatorAccount = accountClient.getAccountById(friendship.getUserIdInitiator());
                dto.setInitiator(initiatorAccount);
            } catch (Exception e) {
                dto.setInitiator(null);
            }
            
            return dto;
        });
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
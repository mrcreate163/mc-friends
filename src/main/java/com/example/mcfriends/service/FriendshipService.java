package com.example.mcfriends.service;

import com.example.mcfriends.client.AccountClient;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.dto.NotificationEvent;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.InvalidStatusException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.exception.SelfFriendshipException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.repository.FriendshipRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления бизнес-логикой дружеских связей.
 * 
 * <p>Отвечает за:</p>
 * <ul>
 *   <li>Валидацию операций с дружбой</li>
 *   <li>Управление жизненным циклом Friendship сущностей</li>
 *   <li>Отправку событий в Kafka через {@link KafkaProducerService}</li>
 *   <li>Бизнес-правила (проверка блокировок, дубликатов заявок)</li>
 *   <li>Интеграцию с account-service для получения данных пользователей</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Slf4j
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

    /**
     * Отправить заявку в друзья.
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Пользователь не может отправить заявку самому себе</li>
     *   <li>Нельзя отправить заявку если пользователи уже друзья</li>
     *   <li>Нельзя отправить заявку если уже есть pending заявка</li>
     *   <li>Нельзя отправить заявку заблокированному пользователю</li>
     *   <li>Можно отправить новую заявку если предыдущая была отклонена</li>
     *   <li>Можно обновить подписку до заявки в друзья</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Создаёт запись Friendship в БД со статусом PENDING</li>
     *   <li>Отправляет Kafka событие FRIEND_REQUEST_SENT</li>
     * </ul>
     * 
     * @param initiatorId UUID пользователя, отправляющего заявку
     * @param targetId UUID пользователя, получающего заявку
     * @return созданный объект Friendship
     * @throws SelfFriendshipException если initiatorId == targetId
     * @throws FriendshipAlreadyExistsException если нарушены бизнес-правила
     * @see #acceptFriendRequest(UUID, UUID) для принятия заявки
     * @see #declineFriendRequest(UUID, UUID) для отклонения заявки
     */
    public Friendship sendFriendRequest(UUID initiatorId, UUID targetId) {
        log.debug("Processing friend request: initiatorId={}, targetId={}", initiatorId, targetId);
        
        // Validate: Cannot send friend request to yourself
        if (initiatorId.equals(targetId)) {
            log.warn("Self friend request attempted: userId={}", initiatorId);
            throw new SelfFriendshipException();
        }

        // Check if friendship already exists
        friendshipRepository.findByUserIds(initiatorId, targetId).ifPresent(friendship -> {
            log.warn("Friend request already exists: initiatorId={}, targetId={}, status={}", 
                    initiatorId, targetId, friendship.getStatus());
            switch (friendship.getStatus()) {
                case PENDING -> throw new FriendshipAlreadyExistsException("Friend request already pending");
                case ACCEPTED -> throw new FriendshipAlreadyExistsException("Users are already friends");
                case BLOCKED -> throw new FriendshipAlreadyExistsException("Cannot send friend request: user is blocked");
                case DECLINED -> {} // Allow new request if previous was declined
                case SUBSCRIBED -> {} // Allow upgrading subscription to friend request
            }
        });

        Friendship request = new Friendship();
        request.setUserIdInitiator(initiatorId);
        request.setUserIdTarget(targetId);
        request.setStatus(FriendshipStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());
        
        Friendship saved = friendshipRepository.save(request);
        log.info("Friendship created: id={}, initiatorId={}, targetId={}", 
                saved.getId(), initiatorId, targetId);
        
        return saved;
    }

    /**
     * Принять заявку в друзья.
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Только целевой пользователь (target) может принять заявку</li>
     *   <li>Заявка должна быть в статусе PENDING</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Обновляет статус Friendship на ACCEPTED</li>
     *   <li>Устанавливает updatedAt</li>
     *   <li>Отправляет Kafka событие FRIEND_REQUEST_ACCEPTED инициатору</li>
     * </ul>
     * 
     * @param requestId UUID заявки в друзья
     * @param currentUserId UUID текущего пользователя (должен быть target)
     * @return обновлённый объект Friendship со статусом ACCEPTED
     * @throws ResourceNotFoundException если заявка с requestId не найдена
     * @throws ForbiddenException если currentUserId не является target
     * @throws InvalidStatusException если заявка уже обработана
     * @see #sendFriendRequest(UUID, UUID) для отправки заявки
     */
    public Friendship acceptFriendRequest(UUID requestId, UUID currentUserId) {
        log.debug("Processing accept friend request: requestId={}, currentUserId={}", requestId, currentUserId);
        
        Friendship request = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Запрос на дружбу с ID " + requestId + " не найден"));

        if (!request.getUserIdTarget().equals(currentUserId)) {
            log.warn("Forbidden accept attempt: requestId={}, currentUserId={}, expectedUserId={}", 
                    requestId, currentUserId, request.getUserIdTarget());
            throw new ForbiddenException("Доступ запрещен: Только целевой пользователь может принять этот запрос.");
        }

        if (request.getStatus() != FriendshipStatus.PENDING) {
            log.warn("Invalid status for accept: requestId={}, status={}", requestId, request.getStatus());
            throw new InvalidStatusException("Запрос уже обработан");
        }

        request.setStatus(FriendshipStatus.ACCEPTED);
        request.setUpdatedAt(LocalDateTime.now());
        Friendship accepted = friendshipRepository.save(request);
        
        log.info("Friend request accepted: requestId={}, initiatorId={}, targetId={}", 
                requestId, accepted.getUserIdInitiator(), currentUserId);

        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_REQUEST_ACCEPTED");
        event.setRecipientId(accepted.getUserIdInitiator());
        event.setSenderId(currentUserId);
        kafkaProducerService.sendNotification(event);

        return accepted;
    }

    /**
     * Отклонить заявку в друзья.
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Только целевой пользователь (target) может отклонить заявку</li>
     *   <li>Заявка должна быть в статусе PENDING</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Обновляет статус Friendship на DECLINED</li>
     *   <li>Устанавливает updatedAt</li>
     *   <li>Отправляет Kafka событие FRIEND_REQUEST_DECLINED инициатору</li>
     * </ul>
     * 
     * @param requestId UUID заявки в друзья
     * @param currentUserId UUID текущего пользователя (должен быть target)
     * @return обновлённый объект Friendship со статусом DECLINED
     * @throws ResourceNotFoundException если заявка с requestId не найдена
     * @throws ForbiddenException если currentUserId не является target
     * @throws InvalidStatusException если заявка уже обработана
     */
    public Friendship declineFriendRequest(UUID requestId, UUID currentUserId) {
        log.debug("Processing decline friend request: requestId={}, currentUserId={}", requestId, currentUserId);
        
        Friendship request = friendshipRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Запрос на дружбу с ID " + requestId + " не найден"));

        if (!request.getUserIdTarget().equals(currentUserId)) {
            log.warn("Forbidden decline attempt: requestId={}, currentUserId={}, expectedUserId={}", 
                    requestId, currentUserId, request.getUserIdTarget());
            throw new ForbiddenException("Доступ запрещен: Только целевой пользователь может отклонить этот запрос.");
        }

        if (request.getStatus() != FriendshipStatus.PENDING) {
            log.warn("Invalid status for decline: requestId={}, status={}", requestId, request.getStatus());
            throw new InvalidStatusException("Запрос уже обработан");
        }

        request.setStatus(FriendshipStatus.DECLINED);
        request.setUpdatedAt(LocalDateTime.now());
        Friendship declined = friendshipRepository.save(request);
        
        log.info("Friend request declined: requestId={}, initiatorId={}, targetId={}", 
                requestId, declined.getUserIdInitiator(), currentUserId);

        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_REQUEST_DECLINED");
        event.setRecipientId(declined.getUserIdInitiator());
        event.setSenderId(currentUserId);
        kafkaProducerService.sendNotification(event);

        return declined;
    }

    /**
     * Получить список входящих заявок в друзья с полной информацией о пользователях.
     * 
     * <p>Возвращает заявки со статусом PENDING, где currentUserId является target (получателем).
     * Для каждой заявки загружается полная информация об инициаторе из account-service.</p>
     * 
     * @param currentUserId UUID текущего пользователя
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница с FriendDto объектами, содержащими информацию об инициаторах
     */
    public Page<FriendDto> getIncomingRequests(UUID currentUserId, Pageable pageable) {
        log.debug("Fetching incoming friend requests: userId={}, page={}, size={}", 
                currentUserId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Friendship> requests = friendshipRepository.findByUserIdTargetAndStatus(
                currentUserId, 
                FriendshipStatus.PENDING, 
                pageable
        );

        Set<UUID> initiatorIds = requests.getContent().stream()
                .map(Friendship::getUserIdInitiator)
                .collect(Collectors.toSet());

        if (initiatorIds.isEmpty()) {
            log.debug("No incoming requests found for userId={}", currentUserId);
            return Page.empty(pageable);
        }

        Map<UUID, AccountDto> accountMap = accountClient
                .getAccountsByIds(new ArrayList<>(initiatorIds))
                .stream()
                .collect(Collectors.toMap(AccountDto::getId, a -> a));

        List<FriendDto> friendDtos = requests.getContent().stream()
                .map(friendship -> {
                    AccountDto account = accountMap.get(friendship.getUserIdInitiator());
                    if (account == null) {
                        return null;
                    }

                    FriendDto dto = new FriendDto();
                    dto.setAccount(account);
                    dto.setStatus(friendship.getStatus());
                    return dto;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("Found {} incoming requests for userId={}", requests.getTotalElements(), currentUserId);
        
        return new PageImpl<>(
                friendDtos,
                pageable,
                requests.getTotalElements()
        );
    }

    /**
     * Получить список всех друзей пользователя (без пагинации).
     * 
     * <p>Возвращает все связи со статусом ACCEPTED, где пользователь
     * является либо initiator, либо target.</p>
     * 
     * @param userId UUID пользователя
     * @return список Friendship объектов со статусом ACCEPTED
     */
    public List<Friendship> getAcceptedFriends(UUID userId) {
        return friendshipRepository.findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                userId, FriendshipStatus.ACCEPTED,
                userId, FriendshipStatus.ACCEPTED
        );
    }

    /**
     * Найти дружескую связь между двумя пользователями в любом направлении.
     * 
     * <p>Проверяет наличие связи где userId1 и userId2 могут быть
     * как initiator, так и target.</p>
     * 
     * @param userId1 UUID первого пользователя
     * @param userId2 UUID второго пользователя
     * @return Optional с Friendship если связь найдена
     */
    private Optional<Friendship> findFriendshipBetweenUsers(UUID userId1, UUID userId2) {
        Optional<Friendship> friendship =
                friendshipRepository.findByUserIdInitiatorAndUserIdTarget(userId1, userId2);

        if (friendship.isEmpty()) {
            friendship = friendshipRepository.findByUserIdInitiatorAndUserIdTarget(userId2, userId1);
        }
        return friendship;
    }

    /**
     * Удалить дружескую связь (удалить из друзей).
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Удаляет запись Friendship из БД</li>
     * </ul>
     * 
     * @param currentUserId UUID текущего пользователя
     * @param friendId UUID пользователя для удаления из друзей
     * @throws ResourceNotFoundException если связь не найдена
     */
    public void deleteFriendship(UUID currentUserId, UUID friendId) {
        log.debug("Deleting friendship: currentUserId={}, friendId={}", currentUserId, friendId);
        
        Friendship friendship = findFriendshipBetweenUsers(currentUserId, friendId)
                .orElseThrow(() -> new ResourceNotFoundException("Связь с пользователем " + friendId + " не найдена."));
        
        friendshipRepository.delete(friendship);
        log.info("Friendship deleted: currentUserId={}, friendId={}", currentUserId, friendId);
    }

    /**
     * Получить список друзей с полной информацией о пользователях (с пагинацией).
     * 
     * <p>Возвращает друзей со статусом ACCEPTED с данными аккаунтов из account-service.</p>
     * 
     * @param userId UUID пользователя
     * @param pageable параметры пагинации
     * @return страница с FriendDto объектами
     */
    public Page<FriendDto> getAcceptedFriendsDetails(UUID userId, Pageable pageable) {
        log.debug("Fetching friends list: userId={}, page={}, size={}", 
                userId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<Friendship> friendships = friendshipRepository.findByUserIdAndStatus(
                userId, 
                FriendshipStatus.ACCEPTED,
                pageable
        );

        List<FriendDto> friendDtos = mapFriendshipsToFriendDtos(userId, friendships.getContent());

        log.debug("Found {} friends for userId={}", friendships.getTotalElements(), userId);
        
        return new PageImpl<>(
                friendDtos,
                pageable,
                friendships.getTotalElements()
        );
    }

    /**
     * Получить список друзей с полной информацией о пользователях (без пагинации).
     * 
     * @param userId UUID пользователя
     * @return список FriendDto объектов
     */
    public List<FriendDto> getAcceptedFriendsDetails(UUID userId) {
        List<Friendship> friendships =
                friendshipRepository.findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                        userId, FriendshipStatus.ACCEPTED,
                        userId, FriendshipStatus.ACCEPTED
                );

        return mapFriendshipsToFriendDtos(userId, friendships);
    }

    /**
     * Получить ID другого пользователя в дружеской связи.
     * 
     * <p>Если userId является initiator, возвращает target, и наоборот.</p>
     * 
     * @param userId UUID текущего пользователя
     * @param friendship объект дружеской связи
     * @return UUID другого пользователя
     */
    private UUID getOtherUserId(UUID userId, Friendship friendship) {
        return friendship.getUserIdInitiator().equals(userId)
                ? friendship.getUserIdTarget()
                : friendship.getUserIdInitiator();
    }

    /**
     * Преобразовать список Friendship объектов в FriendDto с данными аккаунтов.
     * 
     * <p>Загружает информацию об аккаунтах из account-service через Feign Client
     * и объединяет с данными о дружбе.</p>
     * 
     * @param userId UUID текущего пользователя
     * @param friendships список дружеских связей
     * @return список FriendDto с полными данными о друзьях
     */
    private List<FriendDto> mapFriendshipsToFriendDtos(UUID userId, List<Friendship> friendships) {
        Set<UUID> friendIds = friendships.stream()
                .map(f -> getOtherUserId(userId, f))
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
                    UUID friendId = getOtherUserId(userId, f);
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

    /**
     * Получить количество друзей пользователя.
     * 
     * @param userId UUID пользователя
     * @return количество друзей (связей со статусом ACCEPTED)
     */
    public Long getFriendCount(UUID userId) {
        return friendshipRepository.countByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
    }

    /**
     * Получить статус дружеской связи с указанным пользователем.
     * 
     * <p>Возвращает детальную информацию о статусе отношений:</p>
     * <ul>
     *   <li>FRIEND - друзья (ACCEPTED)</li>
     *   <li>PENDING_INCOMING - входящая заявка</li>
     *   <li>PENDING_OUTGOING - исходящая заявка</li>
     *   <li>BLOCKED - заблокирован</li>
     *   <li>SUBSCRIBED - подписка</li>
     *   <li>NONE - нет связи</li>
     * </ul>
     * 
     * @param currentUserId UUID текущего пользователя
     * @param targetUserId UUID целевого пользователя
     * @return DTO с информацией о статусе связи
     */
    public FriendshipStatusDto getFriendshipStatus(UUID currentUserId, UUID targetUserId) {
        FriendshipStatusDto dto = new FriendshipStatusDto();
        dto.setUserId(targetUserId);

        Optional<Friendship> friendshipOpt = friendshipRepository.findByUserIds(currentUserId, targetUserId);

        if (friendshipOpt.isEmpty()) {
            dto.setStatusCode("NONE");
            dto.setFriendship(null);
            return dto;
        }

        Friendship friendship = friendshipOpt.get();
        dto.setFriendship(friendship);

        switch (friendship.getStatus()) {
            case ACCEPTED:
                dto.setStatusCode("FRIEND");
                break;
            case PENDING:
                if (friendship.getUserIdTarget().equals(currentUserId)) {
                    dto.setStatusCode("PENDING_INCOMING");
                } else {
                    dto.setStatusCode("PENDING_OUTGOING");
                }
                break;
            case BLOCKED:
                dto.setStatusCode("BLOCKED");
                break;
            case DECLINED:
                dto.setStatusCode("NONE");
                break;
            case SUBSCRIBED:
                dto.setStatusCode("SUBSCRIBED");
                break;
        }

        return dto;
    }

    /**
     * Заблокировать пользователя.
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Пользователь не может заблокировать самого себя</li>
     *   <li>Если связь существует, обновляет её статус на BLOCKED</li>
     *   <li>Если связи нет, создаёт новую со статусом BLOCKED</li>
     *   <li>Текущий пользователь всегда становится initiator блокировки</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Создаёт или обновляет запись Friendship со статусом BLOCKED</li>
     *   <li>Отправляет Kafka событие FRIEND_BLOCKED</li>
     * </ul>
     * 
     * @param currentUserId UUID пользователя, блокирующего
     * @param targetUserId UUID блокируемого пользователя
     * @throws SelfFriendshipException если currentUserId == targetUserId
     */
    public void blockUser(UUID currentUserId, UUID targetUserId) {
        log.debug("Processing block user: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
        
        // Validate: Cannot block yourself
        if (currentUserId.equals(targetUserId)) {
            log.warn("Self block attempted: userId={}", currentUserId);
            throw new SelfFriendshipException();
        }

        // Find existing relationship between users
        Optional<Friendship> existingFriendship = friendshipRepository.findByUserIds(currentUserId, targetUserId);

        if (existingFriendship.isPresent()) {
            // Update existing relationship to BLOCKED
            Friendship friendship = existingFriendship.get();
            friendship.setStatus(FriendshipStatus.BLOCKED);
            friendship.setUserIdInitiator(currentUserId);
            friendship.setUserIdTarget(targetUserId);
            friendship.setUpdatedAt(LocalDateTime.now());
            friendshipRepository.save(friendship);
            log.info("Friendship updated to blocked: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
        } else {
            // Create new BLOCKED relationship
            Friendship friendship = new Friendship();
            friendship.setUserIdInitiator(currentUserId);
            friendship.setUserIdTarget(targetUserId);
            friendship.setStatus(FriendshipStatus.BLOCKED);
            friendship.setCreatedAt(LocalDateTime.now());
            friendshipRepository.save(friendship);
            log.info("User blocked: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
        }

        // Send Kafka event
        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_BLOCKED");
        event.setRecipientId(targetUserId);
        event.setSenderId(currentUserId);
        kafkaProducerService.sendNotification(event);
    }

    /**
     * Разблокировать пользователя.
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Блокировка должна существовать</li>
     *   <li>Текущий пользователь должен быть initiator блокировки</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Удаляет запись Friendship из БД</li>
     *   <li>Отправляет Kafka событие FRIEND_UNBLOCKED</li>
     * </ul>
     * 
     * @param currentUserId UUID пользователя, разблокирующего
     * @param targetUserId UUID разблокируемого пользователя
     * @throws ResourceNotFoundException если блокировка не найдена
     * @throws ForbiddenException если currentUserId не является initiator блокировки
     */
    public void unblockUser(UUID currentUserId, UUID targetUserId) {
        log.debug("Processing unblock user: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
        
        // Find BLOCKED relationship where current user is initiator
        Optional<Friendship> blockedFriendship = friendshipRepository
                .findByUserIdInitiatorAndUserIdTargetAndStatus(
                        currentUserId, targetUserId, FriendshipStatus.BLOCKED
                );

        if (blockedFriendship.isEmpty()) {
            log.warn("Block not found: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
            throw new ResourceNotFoundException("Блокировка не найдена");
        }

        Friendship friendship = blockedFriendship.get();
        
        // Verify current user is the initiator
        if (!friendship.getUserIdInitiator().equals(currentUserId)) {
            log.warn("Not initiator of block: currentUserId={}, initiatorId={}", 
                    currentUserId, friendship.getUserIdInitiator());
            throw new ForbiddenException("Вы не являетесь инициатором блокировки");
        }

        // Delete the blocked relationship
        friendshipRepository.delete(friendship);
        log.info("User unblocked: currentUserId={}, targetUserId={}", currentUserId, targetUserId);

        // Send Kafka event
        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_UNBLOCKED");
        event.setRecipientId(targetUserId);
        event.setSenderId(currentUserId);
        kafkaProducerService.sendNotification(event);
    }

    /**
     * Подписаться на пользователя.
     * 
     * <p>Создаёт одностороннюю связь типа SUBSCRIBED, позволяющую
     * видеть публичные посты пользователя без заявки в друзья.</p>
     * 
     * <p><b>Бизнес-правила:</b></p>
     * <ul>
     *   <li>Пользователь не может подписаться на самого себя</li>
     *   <li>Связь не должна существовать</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Создаёт запись Friendship со статусом SUBSCRIBED</li>
     *   <li>Отправляет Kafka событие FRIEND_SUBSCRIBED</li>
     * </ul>
     * 
     * @param currentUserId UUID подписывающегося пользователя
     * @param targetUserId UUID пользователя, на которого подписываются
     * @throws SelfFriendshipException если currentUserId == targetUserId
     * @throws FriendshipAlreadyExistsException если связь уже существует
     */
    public void subscribeToUser(UUID currentUserId, UUID targetUserId) {
        log.debug("Processing subscribe: currentUserId={}, targetUserId={}", currentUserId, targetUserId);
        
        // Validate: Cannot subscribe to yourself
        if (currentUserId.equals(targetUserId)) {
            log.warn("Self subscribe attempted: userId={}", currentUserId);
            throw new SelfFriendshipException();
        }

        // Check if relationship already exists
        Optional<Friendship> existingFriendship = friendshipRepository.findByUserIds(currentUserId, targetUserId);
        if (existingFriendship.isPresent()) {
            log.warn("Friendship already exists: currentUserId={}, targetUserId={}, status={}", 
                    currentUserId, targetUserId, existingFriendship.get().getStatus());
            throw new FriendshipAlreadyExistsException("Связь с пользователем уже существует");
        }

        // Create new SUBSCRIBED relationship
        Friendship subscription = new Friendship();
        subscription.setUserIdInitiator(currentUserId);
        subscription.setUserIdTarget(targetUserId);
        subscription.setStatus(FriendshipStatus.SUBSCRIBED);
        subscription.setCreatedAt(LocalDateTime.now());
        friendshipRepository.save(subscription);
        
        log.info("User subscribed: currentUserId={}, targetUserId={}", currentUserId, targetUserId);

        // Send Kafka event
        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_SUBSCRIBED");
        event.setRecipientId(targetUserId);
        event.setSenderId(currentUserId);
        kafkaProducerService.sendNotification(event);
    }

    /**
     * Получить список UUID всех друзей пользователя.
     * 
     * <p>Используется для интеграции с другими сервисами,
     * когда нужны только ID без дополнительной информации.</p>
     * 
     * @param userId UUID пользователя
     * @return список UUID друзей
     */
    public List<UUID> getFriendIds(UUID userId) {
        return friendshipRepository.findFriendIdsByUserId(userId, FriendshipStatus.ACCEPTED);
    }

    /**
     * Получить список UUID всех заблокированных пользователей.
     * 
     * <p>Возвращает только пользователей, заблокированных текущим пользователем
     * (где userId является initiator).</p>
     * 
     * @param userId UUID пользователя
     * @return список UUID заблокированных пользователей
     */
    public List<UUID> getBlockedUserIds(UUID userId) {
        return friendshipRepository.findBlockedUserIdsByInitiator(userId, FriendshipStatus.BLOCKED);
    }
}
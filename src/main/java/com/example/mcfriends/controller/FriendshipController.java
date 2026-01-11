package com.example.mcfriends.controller;

import com.example.mcfriends.client.dto.UserDataDetails;
import com.example.mcfriends.dto.FriendCountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.dto.Message;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.service.FriendshipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST контроллер для управления дружескими связями между пользователями.
 * 
 * <p>Обрабатывает операции:</p>
 * <ul>
 *   <li>Отправка/принятие/отклонение заявок в друзья</li>
 *   <li>Управление списком друзей</li>
 *   <li>Блокировка/разблокировка пользователей</li>
 *   <li>Получение статуса дружбы</li>
 *   <li>Подписка на пользователей</li>
 *   <li>Получение рекомендаций друзей</li>
 * </ul>
 * 
 * <p>Все эндпоинты требуют аутентификации через JWT токен.
 * Текущий пользователь передаётся через {@link UserDataDetails}.</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/friends")
@Tag(name = "Friendship", description = "API для управления дружескими связями")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    /**
     * Отправить заявку в друзья указанному пользователю.
     * 
     * <p>Создаёт новую запись Friendship со статусом PENDING и отправляет
     * Kafka событие FRIEND_REQUEST_SENT в топик ACCOUNT_CHANGES.</p>
     * 
     * @param targetUserId UUID пользователя, которому отправляется заявка
     * @param userDetails данные текущего аутентифицированного пользователя
     * @return ResponseEntity с объектом Friendship о успешной отправке заявки
     * @throws com.example.mcfriends.exception.UserNotFoundException если пользователь с targetUserId не найден
     * @throws com.example.mcfriends.exception.FriendshipAlreadyExistsException если заявка уже существует или пользователи уже друзья
     * @throws com.example.mcfriends.exception.SelfFriendshipException если targetUserId равен ID текущего пользователя
     */
    @PostMapping("/{targetUserId}/request")
    public ResponseEntity<Friendship> sendRequest(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDataDetails userDetails
            ) {
        log.info("Received friend request: initiatorId={}, targetId={}", 
                userDetails.getUserId(), targetUserId);
        
        Friendship newRequest = friendshipService.sendFriendRequest(userDetails.getUserId(), targetUserId);
        
        log.info("Friend request sent successfully: requestId={}", newRequest.getId());
        return ResponseEntity.ok(newRequest);
    }

    /**
     * Принять заявку в друзья.
     * 
     * <p>Обновляет статус заявки на ACCEPTED и отправляет событие в Kafka.</p>
     * 
     * @param requestId UUID заявки в друзья
     * @param userDetails данные текущего пользователя (должен быть получателем заявки)
     * @return ResponseEntity с обновлённым объектом Friendship
     * @throws com.example.mcfriends.exception.ResourceNotFoundException если заявка не найдена
     * @throws com.example.mcfriends.exception.ForbiddenException если текущий пользователь не является получателем
     * @throws com.example.mcfriends.exception.InvalidStatusException если заявка уже обработана
     */
    @PutMapping("/{requestId}/approve")
    public ResponseEntity<Friendship> acceptRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received accept request: requestId={}, userId={}", requestId, userDetails.getUserId());
        
        Friendship accepted = friendshipService.acceptFriendRequest(requestId, userDetails.getUserId());
        
        log.info("Friend request accepted successfully: requestId={}", requestId);
        return ResponseEntity.ok(accepted);
    }

    /**
     * Отклонить заявку в друзья.
     * 
     * <p>Обновляет статус заявки на DECLINED и отправляет событие в Kafka.</p>
     * 
     * @param requestId UUID заявки в друзья
     * @param userDetails данные текущего пользователя (должен быть получателем заявки)
     * @return ResponseEntity с сообщением об успешном отклонении
     * @throws com.example.mcfriends.exception.ResourceNotFoundException если заявка не найдена
     * @throws com.example.mcfriends.exception.ForbiddenException если текущий пользователь не является получателем
     * @throws com.example.mcfriends.exception.InvalidStatusException если заявка уже обработана
     */
    @PutMapping("/requests/{requestId}/decline")
    public ResponseEntity<Map<String, String>> declineRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received decline request: requestId={}, userId={}", requestId, userDetails.getUserId());
        
        friendshipService.declineFriendRequest(requestId, userDetails.getUserId());
        
        log.info("Friend request declined successfully: requestId={}", requestId);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Friend request declined");
        return ResponseEntity.ok(response);
    }

    /**
     * Получить список входящих заявок в друзья.
     * 
     * <p>Возвращает заявки со статусом PENDING, где текущий пользователь является получателем.</p>
     * 
     * @param userDetails данные текущего пользователя
     * @param pageable параметры пагинации (page, size, sort)
     * @return ResponseEntity со страницей FriendDto объектов
     */
    @GetMapping("/requests/incoming")
    public ResponseEntity<Page<FriendDto>> getIncomingRequests(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {
        log.info("Received get incoming requests: userId={}, page={}, size={}", 
                userDetails.getUserId(), pageable.getPageNumber(), pageable.getPageSize());
        
        Page<FriendDto> incomingRequests = friendshipService.getIncomingRequests(userDetails.getUserId(), pageable);
        
        log.debug("Returning {} incoming requests", incomingRequests.getTotalElements());
        return ResponseEntity.ok(incomingRequests);
    }

    /**
     * Получить список друзей текущего пользователя.
     * 
     * <p>Возвращает пользователей со статусом ACCEPTED с полной информацией об аккаунтах.</p>
     * 
     * @param userDetails данные текущего пользователя
     * @param pageable параметры пагинации
     * @return ResponseEntity со страницей FriendDto объектов
     */
    @GetMapping
    public ResponseEntity<Page<FriendDto>> getFriendsList(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {
        log.info("Received get friends list: userId={}, page={}, size={}", 
                userDetails.getUserId(), pageable.getPageNumber(), pageable.getPageSize());
        
        Page<FriendDto> friends = friendshipService.getAcceptedFriendsDetails(userDetails.getUserId(), pageable);
        
        log.debug("Returning {} friends", friends.getTotalElements());
        return ResponseEntity.ok(friends);
    }

    /**
     * Удалить пользователя из друзей.
     * 
     * <p>Удаляет дружескую связь между текущим пользователем и указанным пользователем.</p>
     * 
     * @param friendId UUID пользователя для удаления из друзей
     * @param userDetails данные текущего пользователя
     * @throws com.example.mcfriends.exception.ResourceNotFoundException если связь не найдена
     */
    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(
            @PathVariable UUID friendId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received delete friend: userId={}, friendId={}", userDetails.getUserId(), friendId);
        
        friendshipService.deleteFriendship(userDetails.getUserId(), friendId);
        
        log.info("Friend deleted successfully: userId={}, friendId={}", userDetails.getUserId(), friendId);
    }

    /**
     * Получить количество друзей текущего пользователя.
     * 
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity с объектом FriendCountDto, содержащим счётчик друзей
     */
    @GetMapping("/count")
    public ResponseEntity<FriendCountDto> getFriendCount(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.debug("Received get friend count: userId={}", userDetails.getUserId());
        
        Long count = friendshipService.getFriendCount(userDetails.getUserId());
        FriendCountDto dto = new FriendCountDto();
        dto.setCount(count);
        
        log.debug("Friend count: userId={}, count={}", userDetails.getUserId(), count);
        return ResponseEntity.ok(dto);
    }

    /**
     * Получить статус дружбы с указанным пользователем.
     * 
     * <p>Возвращает детальную информацию о связи: FRIEND, PENDING_INCOMING,
     * PENDING_OUTGOING, BLOCKED, SUBSCRIBED или NONE.</p>
     * 
     * @param userId UUID пользователя для проверки статуса
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity с объектом FriendshipStatusDto
     */
    @GetMapping("/{userId}/status")
    public ResponseEntity<FriendshipStatusDto> getFriendshipStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.debug("Received get friendship status: currentUserId={}, targetUserId={}", 
                userDetails.getUserId(), userId);
        
        FriendshipStatusDto status = friendshipService.getFriendshipStatus(userDetails.getUserId(), userId);
        
        log.debug("Friendship status: statusCode={}", status.getStatusCode());
        return ResponseEntity.ok(status);
    }

    /**
     * Получить рекомендации друзей.
     * 
     * <p><b>Примечание:</b> В текущей версии возвращает пустую страницу.
     * Функционал рекомендаций будет реализован в будущих версиях.</p>
     * 
     * @param userDetails данные текущего пользователя
     * @param pageable параметры пагинации
     * @return ResponseEntity с пустой страницей FriendDto объектов
     */
    @GetMapping("/recommendations")
    @Operation(summary = "Получить рекомендации друзей")
    @ApiResponse(responseCode = "200", description = "Рекомендации успешно получены")
    public ResponseEntity<Page<FriendDto>> getRecommendations(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {
        log.debug("Received get recommendations: userId={}", userDetails.getUserId());
        // Stub: returns empty page
        return ResponseEntity.ok(Page.empty(pageable));
    }

    /**
     * Заблокировать пользователя.
     * 
     * <p>Создаёт или обновляет связь со статусом BLOCKED. Заблокированный пользователь
     * не сможет отправлять заявки в друзья текущему пользователю.</p>
     * 
     * @param uuid UUID пользователя для блокировки
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity с сообщением об успешной блокировке
     * @throws com.example.mcfriends.exception.SelfFriendshipException если попытка заблокировать самого себя
     */
    @PutMapping("/block/{uuid}")
    @Operation(summary = "Заблокировать пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь заблокирован"),
            @ApiResponse(responseCode = "400", description = "Невозможно заблокировать самого себя")
    })
    public ResponseEntity<Message> blockUser(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received block user: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        
        friendshipService.blockUser(userDetails.getUserId(), uuid);
        
        log.info("User blocked successfully: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("User blocked successfully"));
    }

    /**
     * Разблокировать пользователя.
     * 
     * <p>Удаляет блокировку. Только инициатор блокировки может её снять.</p>
     * 
     * @param uuid UUID пользователя для разблокировки
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity с сообщением об успешной разблокировке
     * @throws com.example.mcfriends.exception.ResourceNotFoundException если блокировка не найдена
     * @throws com.example.mcfriends.exception.ForbiddenException если текущий пользователь не является инициатором блокировки
     */
    @PutMapping("/unblock/{uuid}")
    @Operation(summary = "Разблокировать пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Пользователь разблокирован"),
            @ApiResponse(responseCode = "404", description = "Блокировка не найдена"),
            @ApiResponse(responseCode = "403", description = "Вы не являетесь инициатором блокировки")
    })
    public ResponseEntity<Message> unblockUser(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received unblock user: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        
        friendshipService.unblockUser(userDetails.getUserId(), uuid);
        
        log.info("User unblocked successfully: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("User unblocked successfully"));
    }

    /**
     * Подписаться на пользователя.
     * 
     * <p>Создаёт одностороннюю связь типа SUBSCRIBED без отправки заявки в друзья.
     * Позволяет видеть публичные посты пользователя.</p>
     * 
     * @param uuid UUID пользователя для подписки
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity с сообщением об успешной подписке
     * @throws com.example.mcfriends.exception.SelfFriendshipException если попытка подписаться на самого себя
     * @throws com.example.mcfriends.exception.FriendshipAlreadyExistsException если связь уже существует
     */
    @PostMapping("/subscribe/{uuid}")
    @Operation(summary = "Подписаться на пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Подписка выполнена"),
            @ApiResponse(responseCode = "400", description = "Связь уже существует")
    })
    public ResponseEntity<Message> subscribeToUser(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.info("Received subscribe request: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        
        friendshipService.subscribeToUser(userDetails.getUserId(), uuid);
        
        log.info("User subscribed successfully: userId={}, targetUserId={}", userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("Subscribed successfully"));
    }

    /**
     * Получить список ID друзей текущего пользователя.
     * 
     * <p>Возвращает только UUID друзей без дополнительной информации.
     * Используется для интеграции с другими сервисами.</p>
     * 
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity со списком UUID друзей
     */
    @GetMapping("/friendId")
    @Operation(summary = "Получить список ID друзей текущего пользователя")
    @ApiResponse(responseCode = "200", description = "Список ID друзей получен")
    public ResponseEntity<List<UUID>> getFriendIds(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.debug("Received get friend IDs: userId={}", userDetails.getUserId());
        
        List<UUID> friendIds = friendshipService.getFriendIds(userDetails.getUserId());
        
        log.debug("Returning {} friend IDs", friendIds.size());
        return ResponseEntity.ok(friendIds);
    }

    /**
     * Получить список ID друзей конкретного пользователя.
     * 
     * <p>Возвращает UUID друзей указанного пользователя.
     * Используется другими сервисами для получения списка друзей.</p>
     * 
     * @param userId UUID пользователя
     * @return ResponseEntity со списком UUID друзей
     */
    @GetMapping("/friendId/post/{userId}")
    @Operation(summary = "Получить список ID друзей конкретного пользователя")
    @ApiResponse(responseCode = "200", description = "Список ID друзей получен")
    public ResponseEntity<List<UUID>> getFriendIdsForUser(
            @PathVariable UUID userId
    ) {
        log.debug("Received get friend IDs for user: userId={}", userId);
        
        List<UUID> friendIds = friendshipService.getFriendIds(userId);
        
        log.debug("Returning {} friend IDs for user {}", friendIds.size(), userId);
        return ResponseEntity.ok(friendIds);
    }

    /**
     * Получить список ID заблокированных пользователей.
     * 
     * <p>Возвращает UUID пользователей, заблокированных текущим пользователем.</p>
     * 
     * @param userDetails данные текущего пользователя
     * @return ResponseEntity со списком UUID заблокированных пользователей
     */
    @GetMapping("/blockFriendId")
    @Operation(summary = "Получить список ID заблокированных пользователей")
    @ApiResponse(responseCode = "200", description = "Список ID заблокированных получен")
    public ResponseEntity<List<UUID>> getBlockedUserIds(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        log.debug("Received get blocked user IDs: userId={}", userDetails.getUserId());
        
        List<UUID> blockedIds = friendshipService.getBlockedUserIds(userDetails.getUserId());
        
        log.debug("Returning {} blocked user IDs", blockedIds.size());
        return ResponseEntity.ok(blockedIds);
    }


}
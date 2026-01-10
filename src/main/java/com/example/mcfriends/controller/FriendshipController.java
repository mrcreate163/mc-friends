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

@RestController
@RequestMapping("/api/v1/friends")
@Tag(name = "Friendship", description = "API для управления дружескими связями")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/{targetUserId}/request")
    public ResponseEntity<Friendship> sendRequest(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDataDetails userDetails
            ) {
        Friendship newRequest = friendshipService.sendFriendRequest(userDetails.getUserId(), targetUserId);
        return ResponseEntity.ok(newRequest);
    }

    @PutMapping("/{requestId}/approve")
    public ResponseEntity<Friendship> acceptRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {

        Friendship accepted = friendshipService.acceptFriendRequest(requestId, userDetails.getUserId());
        return ResponseEntity.ok(accepted);
    }

    @PutMapping("/requests/{requestId}/decline")
    public ResponseEntity<Map<String, String>> declineRequest(
            @PathVariable UUID requestId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {

        friendshipService.declineFriendRequest(requestId, userDetails.getUserId());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Friend request declined");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<Page<FriendDto>> getIncomingRequests(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {

        Page<FriendDto> incomingRequests = friendshipService.getIncomingRequests(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(incomingRequests);
    }

    @GetMapping
    public ResponseEntity<Page<FriendDto>> getFriendsList(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {

        Page<FriendDto> friends = friendshipService.getAcceptedFriendsDetails(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(friends);
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(
            @PathVariable UUID friendId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {

        friendshipService.deleteFriendship(userDetails.getUserId(), friendId);
    }

    @GetMapping("/count")
    public ResponseEntity<FriendCountDto> getFriendCount(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        Long count = friendshipService.getFriendCount(userDetails.getUserId());
        FriendCountDto dto = new FriendCountDto();
        dto.setCount(count);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{userId}/status")
    public ResponseEntity<FriendshipStatusDto> getFriendshipStatus(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        FriendshipStatusDto status = friendshipService.getFriendshipStatus(userDetails.getUserId(), userId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Получить рекомендации друзей")
    @ApiResponse(responseCode = "200", description = "Рекомендации успешно получены")
    public ResponseEntity<Page<FriendDto>> getRecommendations(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {
        // Stub: returns empty page
        return ResponseEntity.ok(Page.empty(pageable));
    }

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
        friendshipService.blockUser(userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("User blocked successfully"));
    }

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
        friendshipService.unblockUser(userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("User unblocked successfully"));
    }

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
        friendshipService.subscribeToUser(userDetails.getUserId(), uuid);
        return ResponseEntity.ok(new Message("Subscribed successfully"));
    }

    @GetMapping("/friendId")
    @Operation(summary = "Получить список ID друзей текущего пользователя")
    @ApiResponse(responseCode = "200", description = "Список ID друзей получен")
    public ResponseEntity<List<UUID>> getFriendIds(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        List<UUID> friendIds = friendshipService.getFriendIds(userDetails.getUserId());
        return ResponseEntity.ok(friendIds);
    }

    @GetMapping("/friendId/post/{userId}")
    @Operation(summary = "Получить список ID друзей конкретного пользователя")
    @ApiResponse(responseCode = "200", description = "Список ID друзей получен")
    public ResponseEntity<List<UUID>> getFriendIdsForUser(
            @PathVariable UUID userId
    ) {
        List<UUID> friendIds = friendshipService.getFriendIds(userId);
        return ResponseEntity.ok(friendIds);
    }

    @GetMapping("/blockFriendId")
    @Operation(summary = "Получить список ID заблокированных пользователей")
    @ApiResponse(responseCode = "200", description = "Список ID заблокированных получен")
    public ResponseEntity<List<UUID>> getBlockedUserIds(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {
        List<UUID> blockedIds = friendshipService.getBlockedUserIds(userDetails.getUserId());
        return ResponseEntity.ok(blockedIds);
    }


}
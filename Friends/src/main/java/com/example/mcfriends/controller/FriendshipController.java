package com.example.mcfriends.controller;

import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.service.FriendshipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    private UUID extractUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
        return UUID.fromString(userIdHeader);
    }

    @PostMapping("/requests/{targetUserId}")
    public ResponseEntity<Friendship> sendRequest(
            @PathVariable UUID targetUserId,
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader
    ) {
        UUID initiatorId = extractUserId(userIdHeader);
        Friendship newRequest = friendshipService.sendFriendRequest(initiatorId, targetUserId);
        return ResponseEntity.ok(newRequest);
    }

    @PutMapping("/requests/{requestId}/accept")
    public ResponseEntity<Friendship> acceptRequest(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader
    ) {
        UUID currentUserId = extractUserId(userIdHeader);

        Friendship accepted = friendshipService.acceptFriendRequest(requestId, currentUserId);
        return ResponseEntity.ok(accepted);
    }

    @GetMapping
    public ResponseEntity<List<FriendDto>> getFriendsList(
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader
    ) {
        UUID currentUserId = extractUserId(userIdHeader);
        List<FriendDto> friends = friendshipService.getAcceptedFriendsDetails(currentUserId);
        return ResponseEntity.ok(friends);
    }

    @DeleteMapping("/{friendId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(
            @PathVariable UUID friendId,
            @RequestHeader(value = "X-USER-ID", required = false) String userIdHeader
    ) {
        UUID currentUserId = extractUserId(userIdHeader);
        friendshipService.deleteFriendship(currentUserId, friendId);
    }
}
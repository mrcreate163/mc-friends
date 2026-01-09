package com.example.mcfriends.controller;

import com.example.mcfriends.client.dto.UserDataDetails;
import com.example.mcfriends.dto.FriendCountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.service.FriendshipService;
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
public class FriendshipController {

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @PostMapping("/requests/{targetUserId}")
    public ResponseEntity<Friendship> sendRequest(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal UserDataDetails userDetails
            ) {
        Friendship newRequest = friendshipService.sendFriendRequest(userDetails.getUserId(), targetUserId);
        return ResponseEntity.ok(newRequest);
    }

    @PutMapping("/requests/{requestId}/accept")
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
    public ResponseEntity<List<FriendDto>> getFriendsList(
            @AuthenticationPrincipal UserDataDetails userDetails
    ) {

        List<FriendDto> friends = friendshipService.getAcceptedFriendsDetails(userDetails.getUserId());
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
    public ResponseEntity<Page<FriendDto>> getRecommendations(
            @AuthenticationPrincipal UserDataDetails userDetails,
            Pageable pageable
    ) {
        // Заглушка: возвращаем пустую страницу
        return ResponseEntity.ok(Page.empty(pageable));
    }


}
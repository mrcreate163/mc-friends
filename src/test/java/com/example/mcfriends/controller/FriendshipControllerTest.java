package com.example.mcfriends.controller;

import com.example.mcfriends.client.dto.UserDataDetails;
import com.example.mcfriends.config.TestSecurityConfig;
import com.example.mcfriends.dto.FriendCountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.dto.Message;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.exception.SelfFriendshipException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.service.FriendshipService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FriendshipController
 * Tests REST API endpoints for friendship operations
 */
@WebMvcTest(FriendshipController.class)
@Import(TestSecurityConfig.class)
@DisplayName("FriendshipController Unit Tests")
class FriendshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FriendshipService friendshipService;

    private UUID currentUserId;
    private UUID targetUserId;
    private UserDataDetails userDetails;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        targetUserId = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");
        userDetails = new TestSecurityConfig.TestUserDataDetails(currentUserId, "test@example.com");
    }

    private RequestPostProcessor authenticatedUser() {
        return authentication(new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @Nested
    @DisplayName("POST /api/v1/friends/{targetUserId}/request - Send Friend Request")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when friend request sent successfully")
        void sendFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            Friendship friendship = createFriendship(currentUserId, targetUserId, FriendshipStatus.PENDING);
            when(friendshipService.sendFriendRequest(currentUserId, targetUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.userIdInitiator").value(currentUserId.toString()))
                    .andExpect(jsonPath("$.userIdTarget").value(targetUserId.toString()));
        }

        @Test
        @DisplayName("Should return 400 when UUID is invalid")
        void sendFriendRequest_Returns400_WhenInvalidUUID() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", "invalid-uuid")
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 409 when users are already friends")
        void sendFriendRequest_Returns409_WhenAlreadyFriends() throws Exception {
            // Arrange
            when(friendshipService.sendFriendRequest(currentUserId, targetUserId))
                    .thenThrow(new FriendshipAlreadyExistsException("Users are already friends"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 400 when trying to send request to self")
        void sendFriendRequest_Returns400_WhenSelfRequest() throws Exception {
            // Arrange
            when(friendshipService.sendFriendRequest(currentUserId, currentUserId))
                    .thenThrow(new SelfFriendshipException());

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", currentUserId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/{requestId}/approve - Accept Friend Request")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when request accepted successfully")
        void approveFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship friendship = createFriendship(targetUserId, currentUserId, FriendshipStatus.ACCEPTED);
            when(friendshipService.acceptFriendRequest(requestId, currentUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
        }

        @Test
        @DisplayName("Should return 404 when request not found")
        void approveFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(requestId, currentUserId))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 403 when user is not target")
        void approveFriendRequest_Returns403_WhenNotTarget() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(requestId, currentUserId))
                    .thenThrow(new ForbiddenException("Access denied"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/requests/{requestId}/decline - Decline Friend Request")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when request declined successfully")
        void declineFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship friendship = createFriendship(targetUserId, currentUserId, FriendshipStatus.DECLINED);
            when(friendshipService.declineFriendRequest(requestId, currentUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", requestId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Friend request declined"));
        }

        @Test
        @DisplayName("Should return 404 when request not found")
        void declineFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.declineFriendRequest(requestId, currentUserId))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", requestId)
                            .with(authenticatedUser())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/requests/incoming - Get Incoming Requests")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Should return 200 with paged data")
        void getIncomingRequests_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = new FriendDto();
            Page<FriendDto> page = new PageImpl<>(List.of(friendDto), PageRequest.of(0, 10), 1);
            when(friendshipService.getIncomingRequests(eq(currentUserId), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .with(authenticatedUser())
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Should return empty page when no requests")
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() throws Exception {
            // Arrange
            Page<FriendDto> emptyPage = Page.empty(PageRequest.of(0, 10));
            when(friendshipService.getIncomingRequests(eq(currentUserId), any()))
                    .thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends - Get Friends List")
    class GetFriendsListTests {

        @Test
        @DisplayName("Should return 200 with paged data")
        void getFriends_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = new FriendDto();
            Page<FriendDto> page = new PageImpl<>(List.of(friendDto), PageRequest.of(0, 10), 1);
            when(friendshipService.getAcceptedFriendsDetails(eq(currentUserId), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .with(authenticatedUser())
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Should support pageable parameters")
        void getFriends_SupportsPageableParameters() throws Exception {
            // Arrange
            Page<FriendDto> emptyPage = Page.empty(PageRequest.of(2, 20));
            when(friendshipService.getAcceptedFriendsDetails(eq(currentUserId), any()))
                    .thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .with(authenticatedUser())
                            .param("page", "2")
                            .param("size", "20"))
                    .andExpect(status().isOk());

            verify(friendshipService).getAcceptedFriendsDetails(eq(currentUserId), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friends/{friendId} - Remove Friend")
    class RemoveFriendTests {

        @Test
        @DisplayName("Should return 204 when successful")
        void removeFriend_Returns204_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).deleteFriendship(currentUserId, targetUserId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isNoContent());

            verify(friendshipService).deleteFriendship(currentUserId, targetUserId);
        }

        @Test
        @DisplayName("Should return 404 when friendship not found")
        void removeFriend_Returns404_WhenFriendshipNotFound() throws Exception {
            // Arrange
            doThrow(new ResourceNotFoundException("Friendship not found"))
                    .when(friendshipService).deleteFriendship(currentUserId, targetUserId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/count - Get Friends Count")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Should return 200 with count")
        void getFriendsCount_Returns200WithCount() throws Exception {
            // Arrange
            when(friendshipService.getFriendCount(currentUserId)).thenReturn(5L);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/count")
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/{userId}/status - Get Friendship Status")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Should return 200 with status")
        void getFriendshipStatus_Returns200WithStatus() throws Exception {
            // Arrange
            FriendshipStatusDto statusDto = new FriendshipStatusDto();
            statusDto.setUserId(targetUserId);
            statusDto.setStatusCode("FRIEND");
            when(friendshipService.getFriendshipStatus(currentUserId, targetUserId))
                    .thenReturn(statusDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/{userId}/status", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value("FRIEND"))
                    .andExpect(jsonPath("$.userId").value(targetUserId.toString()));
        }

        @Test
        @DisplayName("Should return 200 with NONE status when no relationship")
        void getFriendshipStatus_Returns200_WhenNoRelationship() throws Exception {
            // Arrange
            FriendshipStatusDto statusDto = new FriendshipStatusDto();
            statusDto.setUserId(targetUserId);
            statusDto.setStatusCode("NONE");
            when(friendshipService.getFriendshipStatus(currentUserId, targetUserId))
                    .thenReturn(statusDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/{userId}/status", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value("NONE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/recommendations - Get Friend Recommendations")
    class GetRecommendationsTests {

        @Test
        @DisplayName("Should return 200 with empty list")
        void getRecommendations_Returns200WithEmptyList() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/recommendations")
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/block/{uuid} - Block User")
    class BlockUserTests {

        @Test
        @DisplayName("Should return 200 when user blocked successfully")
        void blockUser_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).blockUser(currentUserId, targetUserId);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/block/{uuid}", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User blocked successfully"));

            verify(friendshipService).blockUser(currentUserId, targetUserId);
        }

        @Test
        @DisplayName("Should return 400 when trying to block self")
        void blockUser_Returns400_WhenSelfBlock() throws Exception {
            // Arrange
            doThrow(new SelfFriendshipException())
                    .when(friendshipService).blockUser(currentUserId, currentUserId);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/block/{uuid}", currentUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/unblock/{uuid} - Unblock User")
    class UnblockUserTests {

        @Test
        @DisplayName("Should return 200 when user unblocked successfully")
        void unblockUser_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).unblockUser(currentUserId, targetUserId);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/unblock/{uuid}", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User unblocked successfully"));

            verify(friendshipService).unblockUser(currentUserId, targetUserId);
        }

        @Test
        @DisplayName("Should return 404 when block not found")
        void unblockUser_Returns404_WhenBlockNotFound() throws Exception {
            // Arrange
            doThrow(new ResourceNotFoundException("Block not found"))
                    .when(friendshipService).unblockUser(currentUserId, targetUserId);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/unblock/{uuid}", targetUserId)
                            .with(authenticatedUser()))
                    .andExpect(status().isNotFound());
        }
    }

    // Helper methods
    private Friendship createFriendship(UUID initiatorId, UUID targetId, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setId(UUID.randomUUID());
        friendship.setUserIdInitiator(initiatorId);
        friendship.setUserIdTarget(targetId);
        friendship.setStatus(status);
        friendship.setCreatedAt(LocalDateTime.now());
        return friendship;
    }
}

package com.example.mcfriends.controller;

import com.example.mcfriends.client.dto.UserDto;
import com.example.mcfriends.config.TestSecurityConfig;
import com.example.mcfriends.dto.FriendCountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.exception.SelfFriendshipException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.service.FriendshipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

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
 * Unit tests for FriendshipController.
 * Tests all REST endpoints with mocked service layer.
 * Target coverage: 85%+
 */
@WebMvcTest(controllers = FriendshipController.class, 
            excludeAutoConfiguration = {SecurityAutoConfiguration.class})
@DisplayName("FriendshipController Unit Tests")
class FriendshipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FriendshipService friendshipService;

    private static final UUID TEST_USER_ID = TestSecurityConfig.TEST_USER_ID;
    private static final UUID TARGET_USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken createAuthentication() {
        UserDto userDto = new UserDto(TEST_USER_ID, "test@example.com");
        return new UsernamePasswordAuthenticationToken(userDto, null, List.of());
    }

    @Nested
    @DisplayName("POST /api/v1/friends/{targetUserId}/request")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when successfully sending friend request")
        void sendFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            Friendship friendship = createFriendship(TEST_USER_ID, TARGET_USER_ID, FriendshipStatus.PENDING);
            when(friendshipService.sendFriendRequest(TEST_USER_ID, TARGET_USER_ID)).thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", TARGET_USER_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.userIdInitiator").value(TEST_USER_ID.toString()))
                    .andExpect(jsonPath("$.userIdTarget").value(TARGET_USER_ID.toString()));

            verify(friendshipService).sendFriendRequest(TEST_USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should return 400 when trying to send request to self")
        void sendFriendRequest_Returns400_WhenSelfRequest() throws Exception {
            // Arrange
            when(friendshipService.sendFriendRequest(TEST_USER_ID, TEST_USER_ID))
                    .thenThrow(new SelfFriendshipException());

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", TEST_USER_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 409 when users are already friends")
        void sendFriendRequest_Returns409_WhenAlreadyFriends() throws Exception {
            // Arrange
            when(friendshipService.sendFriendRequest(TEST_USER_ID, TARGET_USER_ID))
                    .thenThrow(new FriendshipAlreadyExistsException("Users are already friends"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", TARGET_USER_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Should return 401 when unauthorized")
        void sendFriendRequest_Returns401_WhenUnauthorized() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verify(friendshipService, never()).sendFriendRequest(any(), any());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/{requestId}/approve")
    class ApproveFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when successfully approving request")
        void approveFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            Friendship friendship = createFriendship(TARGET_USER_ID, TEST_USER_ID, FriendshipStatus.ACCEPTED);
            when(friendshipService.acceptFriendRequest(REQUEST_ID, TEST_USER_ID)).thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", REQUEST_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));

            verify(friendshipService).acceptFriendRequest(REQUEST_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when request not found")
        void approveFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            when(friendshipService.acceptFriendRequest(REQUEST_ID, TEST_USER_ID))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", REQUEST_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 403 when user is not target")
        void approveFriendRequest_Returns403_WhenNotTarget() throws Exception {
            // Arrange
            when(friendshipService.acceptFriendRequest(REQUEST_ID, TEST_USER_ID))
                    .thenThrow(new ForbiddenException("Access denied"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", REQUEST_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/requests/{requestId}/decline")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Should return 200 when successfully declining request")
        void declineFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            Friendship friendship = createFriendship(TARGET_USER_ID, TEST_USER_ID, FriendshipStatus.DECLINED);
            when(friendshipService.declineFriendRequest(REQUEST_ID, TEST_USER_ID)).thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", REQUEST_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Friend request declined"));

            verify(friendshipService).declineFriendRequest(REQUEST_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when request not found")
        void declineFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            when(friendshipService.declineFriendRequest(REQUEST_ID, TEST_USER_ID))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", REQUEST_ID)
                            .with(authentication(createAuthentication()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/requests/incoming")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Should return 200 with paged data")
        void getIncomingRequests_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = createFriendDto(TARGET_USER_ID, "Target User");
            Page<FriendDto> page = new PageImpl<>(List.of(friendDto), PageRequest.of(0, 10), 1);
            when(friendshipService.getIncomingRequests(eq(TEST_USER_ID), any())).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .with(authentication(createAuthentication()))
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].account.id").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("Should return empty page when no requests")
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() throws Exception {
            // Arrange
            Page<FriendDto> emptyPage = Page.empty();
            when(friendshipService.getIncomingRequests(eq(TEST_USER_ID), any())).thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends")
    class GetFriendsTests {

        @Test
        @DisplayName("Should return 200 with paged data")
        void getFriends_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = createFriendDto(TARGET_USER_ID, "Friend User");
            Page<FriendDto> page = new PageImpl<>(List.of(friendDto), PageRequest.of(0, 10), 1);
            when(friendshipService.getAcceptedFriendsDetails(eq(TEST_USER_ID), any())).thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .with(authentication(createAuthentication()))
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].account.id").value(TARGET_USER_ID.toString()));
        }

        @Test
        @DisplayName("Should support pageable parameters")
        void getFriends_SupportsPageableParameters() throws Exception {
            // Arrange
            when(friendshipService.getAcceptedFriendsDetails(eq(TEST_USER_ID), any()))
                    .thenReturn(Page.empty());

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .with(authentication(createAuthentication()))
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "createdAt,desc"))
                    .andExpect(status().isOk());

            verify(friendshipService).getAcceptedFriendsDetails(eq(TEST_USER_ID), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/friends/{friendId}")
    class RemoveFriendTests {

        @Test
        @DisplayName("Should return 204 when successfully removing friend")
        void removeFriend_Returns204_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).deleteFriendship(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isNoContent());

            verify(friendshipService).deleteFriendship(TEST_USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when friendship not found")
        void removeFriend_Returns404_WhenFriendshipNotFound() throws Exception {
            // Arrange
            doThrow(new ResourceNotFoundException("Friendship not found"))
                    .when(friendshipService).deleteFriendship(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/count")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Should return 200 with count")
        void getFriendsCount_Returns200WithCount() throws Exception {
            // Arrange
            when(friendshipService.getFriendCount(TEST_USER_ID)).thenReturn(5L);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/count")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));

            verify(friendshipService).getFriendCount(TEST_USER_ID);
        }

        @Test
        @DisplayName("Should return 0 when no friends")
        void getFriendsCount_Returns0_WhenNoFriends() throws Exception {
            // Arrange
            when(friendshipService.getFriendCount(TEST_USER_ID)).thenReturn(0L);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/count")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/{userId}/status")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Should return 200 with status")
        void getFriendshipStatus_Returns200WithStatus() throws Exception {
            // Arrange
            FriendshipStatusDto statusDto = new FriendshipStatusDto();
            statusDto.setUserId(TARGET_USER_ID);
            statusDto.setStatusCode("FRIEND");
            when(friendshipService.getFriendshipStatus(TEST_USER_ID, TARGET_USER_ID)).thenReturn(statusDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/{userId}/status", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(TARGET_USER_ID.toString()))
                    .andExpect(jsonPath("$.statusCode").value("FRIEND"));

            verify(friendshipService).getFriendshipStatus(TEST_USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should return NONE status when no relationship")
        void getFriendshipStatus_ReturnsNone_WhenNoRelationship() throws Exception {
            // Arrange
            FriendshipStatusDto statusDto = new FriendshipStatusDto();
            statusDto.setUserId(TARGET_USER_ID);
            statusDto.setStatusCode("NONE");
            when(friendshipService.getFriendshipStatus(TEST_USER_ID, TARGET_USER_ID)).thenReturn(statusDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/{userId}/status", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value("NONE"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/recommendations")
    class GetRecommendationsTests {

        @Test
        @DisplayName("Should return 200 with empty list")
        void getRecommendations_Returns200WithEmptyList() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/recommendations")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());

            // Note: Service is not called because this is a stub implementation
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/block/{uuid}")
    class BlockUserTests {

        @Test
        @DisplayName("Should return 200 when successfully blocking user")
        void blockUser_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).blockUser(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/block/{uuid}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User blocked successfully"));

            verify(friendshipService).blockUser(TEST_USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should return 400 when trying to block self")
        void blockUser_Returns400_WhenSelfBlock() throws Exception {
            // Arrange
            doThrow(new SelfFriendshipException()).when(friendshipService).blockUser(TEST_USER_ID, TEST_USER_ID);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/block/{uuid}", TEST_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/friends/unblock/{uuid}")
    class UnblockUserTests {

        @Test
        @DisplayName("Should return 200 when successfully unblocking user")
        void unblockUser_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            doNothing().when(friendshipService).unblockUser(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/unblock/{uuid}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User unblocked successfully"));

            verify(friendshipService).unblockUser(TEST_USER_ID, TARGET_USER_ID);
        }

        @Test
        @DisplayName("Should return 404 when block not found")
        void unblockUser_Returns404_WhenBlockNotFound() throws Exception {
            // Arrange
            doThrow(new ResourceNotFoundException("Block not found"))
                    .when(friendshipService).unblockUser(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/unblock/{uuid}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 403 when user is not initiator")
        void unblockUser_Returns403_WhenNotInitiator() throws Exception {
            // Arrange
            doThrow(new ForbiddenException("Not initiator"))
                    .when(friendshipService).unblockUser(TEST_USER_ID, TARGET_USER_ID);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/unblock/{uuid}", TARGET_USER_ID)
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/friendId")
    class GetFriendIdsTests {

        @Test
        @DisplayName("Should return 200 with list of friend IDs")
        void getFriendIds_Returns200WithList() throws Exception {
            // Arrange
            List<UUID> friendIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            when(friendshipService.getFriendIds(TEST_USER_ID)).thenReturn(friendIds);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/friendId")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));

            verify(friendshipService).getFriendIds(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/friends/blockFriendId")
    class GetBlockedUserIdsTests {

        @Test
        @DisplayName("Should return 200 with list of blocked user IDs")
        void getBlockedUserIds_Returns200WithList() throws Exception {
            // Arrange
            List<UUID> blockedIds = List.of(UUID.randomUUID());
            when(friendshipService.getBlockedUserIds(TEST_USER_ID)).thenReturn(blockedIds);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/blockFriendId")
                            .with(authentication(createAuthentication())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(friendshipService).getBlockedUserIds(TEST_USER_ID);
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

    private FriendDto createFriendDto(UUID userId, String username) {
        AccountDto accountDto = new AccountDto();
        accountDto.setId(userId);
        accountDto.setUsername(username);

        FriendDto friendDto = new FriendDto();
        friendDto.setAccount(accountDto);
        friendDto.setStatus(FriendshipStatus.ACCEPTED);
        return friendDto;
    }
}

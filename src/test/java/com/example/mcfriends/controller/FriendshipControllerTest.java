package com.example.mcfriends.controller;

import com.example.mcfriends.client.dto.UserDataDetails;
import com.example.mcfriends.config.TestSecurityConfig;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.service.FriendshipService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for FriendshipController
 * Tests all REST endpoints with mocked service layer
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

    private UUID testUserId = TestSecurityConfig.TEST_USER_ID;
    private UUID targetUserId = TestSecurityConfig.TEST_TARGET_ID;

    private UserDataDetails createMockUserDetails(UUID userId) {
        return new UserDataDetails() {
            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public String getEmail() {
                return "test@example.com";
            }
        };
    }

    @Nested
    @DisplayName("sendFriendRequest Tests")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Returns 200 when successful")
        @WithMockUser
        void sendFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            Friendship friendship = new Friendship();
            friendship.setId(UUID.randomUUID());
            friendship.setUserIdInitiator(testUserId);
            friendship.setUserIdTarget(targetUserId);
            friendship.setStatus(FriendshipStatus.PENDING);
            friendship.setCreatedAt(LocalDateTime.now());

            when(friendshipService.sendFriendRequest(testUserId, targetUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                            .with(authentication(org.springframework.security.core.authority.AuthorityUtils
                                    .createAuthorityList("ROLE_USER"))
                                    .andReturn()
                                    .getAuthentication())
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.userIdInitiator").value(testUserId.toString()))
                    .andExpect(jsonPath("$.userIdTarget").value(targetUserId.toString()));

            verify(friendshipService).sendFriendRequest(testUserId, targetUserId);
        }

        @Test
        @DisplayName("Returns 409 when already friends")
        @WithMockUser
        void sendFriendRequest_Returns409_WhenAlreadyFriends() throws Exception {
            // Arrange
            when(friendshipService.sendFriendRequest(testUserId, targetUserId))
                    .thenThrow(new FriendshipAlreadyExistsException("Users are already friends"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("approveFriendRequest Tests")
    class ApproveFriendRequestTests {

        @Test
        @DisplayName("Returns 200 when successful")
        @WithMockUser
        void approveFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship friendship = new Friendship();
            friendship.setId(requestId);
            friendship.setUserIdInitiator(targetUserId);
            friendship.setUserIdTarget(testUserId);
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendship.setCreatedAt(LocalDateTime.now());

            when(friendshipService.acceptFriendRequest(requestId, testUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));

            verify(friendshipService).acceptFriendRequest(requestId, testUserId);
        }

        @Test
        @DisplayName("Returns 404 when request not found")
        @WithMockUser
        void approveFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(requestId, testUserId))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Returns 403 when not target")
        @WithMockUser
        void approveFriendRequest_Returns403_WhenNotTarget() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(requestId, testUserId))
                    .thenThrow(new ForbiddenException("Access denied"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("declineFriendRequest Tests")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Returns 200 when successful")
        @WithMockUser
        void declineFriendRequest_Returns200_WhenSuccessful() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship friendship = new Friendship();
            friendship.setId(requestId);
            friendship.setStatus(FriendshipStatus.DECLINED);

            when(friendshipService.declineFriendRequest(requestId, testUserId))
                    .thenReturn(friendship);

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", requestId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Friend request declined"));

            verify(friendshipService).declineFriendRequest(requestId, testUserId);
        }

        @Test
        @DisplayName("Returns 404 when request not found")
        @WithMockUser
        void declineFriendRequest_Returns404_WhenRequestNotFound() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.declineFriendRequest(requestId, testUserId))
                    .thenThrow(new ResourceNotFoundException("Request not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/requests/{requestId}/decline", requestId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("getIncomingRequests Tests")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Returns 200 with paged data")
        @WithMockUser
        void getIncomingRequests_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = new FriendDto();
            friendDto.setStatus(FriendshipStatus.PENDING);

            Page<FriendDto> page = new PageImpl<>(
                    Collections.singletonList(friendDto),
                    PageRequest.of(0, 10),
                    1
            );

            when(friendshipService.getIncomingRequests(eq(testUserId), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .principal(createMockUserDetails(testUserId))
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"));

            verify(friendshipService).getIncomingRequests(eq(testUserId), any());
        }

        @Test
        @DisplayName("Returns empty page when no requests")
        @WithMockUser
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() throws Exception {
            // Arrange
            Page<FriendDto> emptyPage = Page.empty(PageRequest.of(0, 10));
            when(friendshipService.getIncomingRequests(eq(testUserId), any()))
                    .thenReturn(emptyPage);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/requests/incoming")
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested
    @DisplayName("getFriends Tests")
    class GetFriendsTests {

        @Test
        @DisplayName("Returns 200 with paged data")
        @WithMockUser
        void getFriends_Returns200WithPagedData() throws Exception {
            // Arrange
            FriendDto friendDto = new FriendDto();
            friendDto.setStatus(FriendshipStatus.ACCEPTED);

            Page<FriendDto> page = new PageImpl<>(
                    Collections.singletonList(friendDto),
                    PageRequest.of(0, 10),
                    1
            );

            when(friendshipService.getAcceptedFriendsDetails(eq(testUserId), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .principal(createMockUserDetails(testUserId))
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());

            verify(friendshipService).getAcceptedFriendsDetails(eq(testUserId), any());
        }

        @Test
        @DisplayName("Supports pageable parameters")
        @WithMockUser
        void getFriends_SupportsPageableParameters() throws Exception {
            // Arrange
            Page<FriendDto> page = Page.empty(PageRequest.of(1, 20));
            when(friendshipService.getAcceptedFriendsDetails(eq(testUserId), any()))
                    .thenReturn(page);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends")
                            .principal(createMockUserDetails(testUserId))
                            .param("page", "1")
                            .param("size", "20")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("removeFriend Tests")
    class RemoveFriendTests {

        @Test
        @DisplayName("Returns 204 when successful")
        @WithMockUser
        void removeFriend_Returns204_WhenSuccessful() throws Exception {
            // Arrange
            UUID friendId = UUID.randomUUID();
            doNothing().when(friendshipService).deleteFriendship(testUserId, friendId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", friendId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(friendshipService).deleteFriendship(testUserId, friendId);
        }

        @Test
        @DisplayName("Returns 404 when friendship not found")
        @WithMockUser
        void removeFriend_Returns404_WhenFriendshipNotFound() throws Exception {
            // Arrange
            UUID friendId = UUID.randomUUID();
            doThrow(new ResourceNotFoundException("Friendship not found"))
                    .when(friendshipService).deleteFriendship(testUserId, friendId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/friends/{friendId}", friendId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("getFriendsCount Tests")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Returns 200 with count")
        @WithMockUser
        void getFriendsCount_Returns200WithCount() throws Exception {
            // Arrange
            when(friendshipService.getFriendCount(testUserId)).thenReturn(5L);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/count")
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));

            verify(friendshipService).getFriendCount(testUserId);
        }
    }

    @Nested
    @DisplayName("getFriendshipStatus Tests")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Returns 200 with status")
        @WithMockUser
        void getFriendshipStatus_Returns200WithStatus() throws Exception {
            // Arrange
            FriendshipStatusDto statusDto = new FriendshipStatusDto();
            statusDto.setUserId(targetUserId);
            statusDto.setStatusCode("FRIEND");

            when(friendshipService.getFriendshipStatus(testUserId, targetUserId))
                    .thenReturn(statusDto);

            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/{userId}/status", targetUserId)
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCode").value("FRIEND"))
                    .andExpect(jsonPath("$.userId").value(targetUserId.toString()));

            verify(friendshipService).getFriendshipStatus(testUserId, targetUserId);
        }
    }

    @Nested
    @DisplayName("getRecommendations Tests")
    class GetRecommendationsTests {

        @Test
        @DisplayName("Returns 200 with empty list")
        @WithMockUser
        void getRecommendations_Returns200WithEmptyList() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/friends/recommendations")
                            .principal(createMockUserDetails(testUserId))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }
}

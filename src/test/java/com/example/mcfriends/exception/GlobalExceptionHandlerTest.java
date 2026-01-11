package com.example.mcfriends.exception;

import com.example.mcfriends.config.TestSecurityConfig;
import com.example.mcfriends.controller.FriendshipController;
import com.example.mcfriends.service.FriendshipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for GlobalExceptionHandler
 * Verifies that exceptions are properly handled and mapped to HTTP responses
 */
@WebMvcTest(FriendshipController.class)
@Import(TestSecurityConfig.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FriendshipService friendshipService;

    @Nested
    @DisplayName("ResourceNotFoundException Tests")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Returns 404 for ResourceNotFoundException")
        @WithMockUser
        void handleResourceNotFoundException_Returns404() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(any(), any()))
                    .thenThrow(new ResourceNotFoundException("Resource not found"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Resource not found"))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.details").exists());
        }
    }

    @Nested
    @DisplayName("IllegalArgumentException Tests")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("Returns 400 for SelfFriendshipException")
        @WithMockUser
        void handleSelfFriendshipException_Returns400() throws Exception {
            // Arrange
            UUID targetId = UUID.randomUUID();
            when(friendshipService.sendFriendRequest(any(), any()))
                    .thenThrow(new SelfFriendshipException());

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("Returns 400 for InvalidStatusException")
        @WithMockUser
        void handleInvalidStatusException_Returns400() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(any(), any()))
                    .thenThrow(new InvalidStatusException("Invalid status"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid status"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("FriendshipAlreadyExistsException Tests")
    class FriendshipAlreadyExistsExceptionTests {

        @Test
        @DisplayName("Returns 409 for FriendshipAlreadyExistsException")
        @WithMockUser
        void handleFriendshipAlreadyExists_Returns409() throws Exception {
            // Arrange
            UUID targetId = UUID.randomUUID();
            when(friendshipService.sendFriendRequest(any(), any()))
                    .thenThrow(new FriendshipAlreadyExistsException("Already friends"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Already friends"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("ForbiddenException Tests")
    class ForbiddenExceptionTests {

        @Test
        @DisplayName("Returns 403 for ForbiddenException")
        @WithMockUser
        void handleForbiddenException_Returns403() throws Exception {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipService.acceptFriendRequest(any(), any()))
                    .thenThrow(new ForbiddenException("Access denied"));

            // Act & Assert
            mockMvc.perform(put("/api/v1/friends/{requestId}/approve", requestId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Access denied"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Nested
    @DisplayName("GenericException Tests")
    class GenericExceptionTests {

        @Test
        @DisplayName("Returns 500 for generic Exception")
        @WithMockUser
        void handleGenericException_Returns500() throws Exception {
            // Arrange
            UUID targetId = UUID.randomUUID();
            when(friendshipService.sendFriendRequest(any(), any()))
                    .thenThrow(new RuntimeException("Unexpected error"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }
}

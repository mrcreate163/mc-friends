package com.example.mcfriends.exception;

import com.example.mcfriends.config.TestSecurityConfig;
import com.example.mcfriends.controller.FriendshipController;
import com.example.mcfriends.service.FriendshipService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for GlobalExceptionHandler
 * Tests exception handling and error response formatting
 */
@WebMvcTest(FriendshipController.class)
@Import(TestSecurityConfig.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FriendshipService friendshipService;

    private final UUID currentUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private final UUID targetUserId = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");

    private RequestPostProcessor authenticatedUser() {
        TestSecurityConfig.TestUserDataDetails userDetails = 
                new TestSecurityConfig.TestUserDataDetails(currentUserId, "test@example.com");
        return authentication(new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        ));
    }

    @Test
    @DisplayName("Should handle ResourceNotFoundException and return 404")
    void handleEntityNotFoundException_Returns404() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new ResourceNotFoundException("User not found"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").exists());
    }

    @Test
    @DisplayName("Should handle SelfFriendshipException and return 400")
    void handleSelfFriendshipException_Returns400() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new SelfFriendshipException());

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should handle FriendshipAlreadyExistsException and return 409")
    void handleFriendshipAlreadyExistsException_Returns409() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new FriendshipAlreadyExistsException("Users are already friends"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Users are already friends"));
    }

    @Test
    @DisplayName("Should handle ForbiddenException and return 403")
    void handleForbiddenException_Returns403() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new ForbiddenException("Access denied"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    @DisplayName("Should handle InvalidStatusException and return 400")
    void handleInvalidStatusException_Returns400() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new InvalidStatusException("Invalid status"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid status"));
    }

    @Test
    @DisplayName("Should handle generic RuntimeException and return 400")
    void handleRuntimeException_Returns400() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unexpected error"));
    }

    @Test
    @DisplayName("Should handle generic Exception and return 500")
    void handleGenericException_Returns500() throws Exception {
        // Arrange
        when(friendshipService.sendFriendRequest(any(), any()))
                .thenThrow(new Exception("Internal server error"));

        // Act & Assert
        mockMvc.perform(post("/api/v1/friends/{targetUserId}/request", targetUserId)
                        .with(authenticatedUser()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Произошла внутренняя ошибка сервера"));
    }
}

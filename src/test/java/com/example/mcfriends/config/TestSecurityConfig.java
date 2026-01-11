package com.example.mcfriends.config;

import com.example.mcfriends.client.dto.UserDataDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

/**
 * Test configuration for security context in tests
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public UserDataDetails testUserDetails() {
        return new TestUserDataDetails(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "test@example.com"
        );
    }

    public static class TestUserDataDetails implements UserDataDetails {
        private final UUID userId;
        private final String email;

        public TestUserDataDetails(UUID userId, String email) {
            this.userId = userId;
            this.email = email;
        }

        @Override
        public UUID getUserId() {
            return userId;
        }

        @Override
        public String getEmail() {
            return email;
        }
    }
}

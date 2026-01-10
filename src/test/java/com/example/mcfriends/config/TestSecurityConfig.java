package com.example.mcfriends.config;

import com.example.mcfriends.client.dto.UserDataDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

/**
 * Test configuration for Security context in tests.
 * Provides a test user for authentication purposes.
 */
@TestConfiguration
public class TestSecurityConfig {
    
    public static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    public static final UUID TEST_USER_2_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
    public static final String TEST_USER_EMAIL = "test@example.com";
    public static final String TEST_USER_USERNAME = "Test User";
    
    @Bean
    @Primary
    public UserDataDetails testUserDetails() {
        return new UserDataDetails() {
            @Override
            public UUID getUserId() {
                return TEST_USER_ID;
            }

            @Override
            public String getEmail() {
                return TEST_USER_EMAIL;
            }
        };
    }
}

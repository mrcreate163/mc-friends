package com.example.mcfriends.config;

import com.example.mcfriends.client.dto.UserDataDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

/**
 * Test configuration for security context
 */
@TestConfiguration
public class TestSecurityConfig {

    public static final UUID TEST_USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    public static final UUID TEST_FRIEND_ID = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");
    public static final UUID TEST_TARGET_ID = UUID.fromString("323e4567-e89b-12d3-a456-426614174002");
    public static final String TEST_EMAIL = "test@example.com";

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
                return TEST_EMAIL;
            }
        };
    }
}

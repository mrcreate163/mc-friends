package com.example.mcfriends.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AuthClient {

    private final static String VALIDATE_URI = "/api/v1/auth/validate?token=";

    private final RestClient restClient;
    private final String authUrl;

    public AuthClient(
            @Value("${client.auth}") String authUrl,
            RestClient.Builder restClient
    ) {
        this.authUrl = authUrl;
        this.restClient = restClient.baseUrl(authUrl).build();
    }

    public Boolean checkValidateToken(String token) {
        try {
            String validateUrl = VALIDATE_URI + token;
            log.info("{}{}", authUrl, validateUrl);
            return restClient.get()
                    .uri(validateUrl)
                    .retrieve()
                    .toEntity(Boolean.class)
                    .getBody();
        } catch (Exception ex) {
            log.warn("Validate Token failed: {}", ex.getMessage());
            return false;
        }
    }
}

package com.example.mcfriends.client;

import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.service.spi.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * HTTP клиент для взаимодействия с mc-account через internal API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.account.url}")
    private String accountServiceUrl;

    /**
     * Получить аккаунт по ID
     */
    public AccountDto getAccountById(java.util.UUID userId) {
        try {
            String url = accountServiceUrl + "/" + userId;
            log.debug("Fetching account: GET {}", url);

            ResponseEntity<AccountDto> response = restTemplate.getForEntity(
                    url,
                    AccountDto.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Account fetched successfully: userId={}", userId);
                return response.getBody();
            }
            throw new UserNotFoundException("Account fetched failed: " + response.getStatusCode());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Account not found: userId={}", userId);
            throw new UserNotFoundException("User not found: " + userId);

        } catch (HttpServerErrorException e) {
            log.error("Account service error: {} {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException("Account service error: " + e.getMessage());

        } catch (ResourceAccessException e) {
            log.error("Account service unavailable: {}", e.getMessage());
            throw new ServiceException("Account service unavailable");
        }

    }

    public List<AccountDto> getAccountsByIds(List<UUID> userIds) {
        try {
            if (userIds == null || userIds.isEmpty()) {
                return Collections.emptyList();
            }

            String idsParam = userIds.stream()
                    .map(UUID::toString)
                    .collect(Collectors.joining(","));

            String url = accountServiceUrl + "?ids=" + idsParam;
            log.debug("Fetching accounts: GET {}", url);

            ResponseEntity<AccountDto[]> response = restTemplate.getForEntity(url, AccountDto[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<AccountDto> accounts = Arrays.asList(response.getBody());
                log.debug("Accounts fetched successfully: count={}", accounts.size());
                return accounts;
            }

            throw new UserNotFoundException("Accounts fetch failed: " + response.getStatusCode());
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Accounts not found: ids={}", userIds);
            throw new UserNotFoundException("Users not found: " + userIds);
        } catch (HttpServerErrorException e) {
            log.error("Account service error: {} {}", e.getStatusCode(), e.getMessage());
            throw new org.hibernate.service.spi.ServiceException("Account service error: " + e.getMessage());
        } catch (ResourceAccessException e) {
            log.error("Account service unavailable: {}", e.getMessage());
            throw new org.hibernate.service.spi.ServiceException("Account service unavailable");
        }
    }



}

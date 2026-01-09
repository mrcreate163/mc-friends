package com.example.mcfriends.client;

import com.example.mcfriends.dto.AccountDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "MC-ACCOUNT")
public interface AccountServiceClient {

    @GetMapping("/api/v1/accounts/{userId}")
    AccountDto getAccountById(@PathVariable("userId") UUID userId);

    @GetMapping("/api/v1/accounts/batch")
    List<AccountDto> getAccountsByIds(@RequestParam("ids") List<UUID> userIds);
}
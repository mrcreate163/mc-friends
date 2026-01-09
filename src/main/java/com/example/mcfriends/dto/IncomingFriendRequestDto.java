package com.example.mcfriends.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class IncomingFriendRequestDto {
    private UUID requestId;
    private AccountDto initiator;
    private LocalDateTime createdAt;
}

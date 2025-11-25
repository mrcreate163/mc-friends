package com.example.mcfriends.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class FriendRequestDto {
    private UUID targetUserId;
}
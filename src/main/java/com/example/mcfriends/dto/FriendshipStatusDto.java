package com.example.mcfriends.dto;

import com.example.mcfriends.model.Friendship;
import lombok.Data;

import java.util.UUID;

@Data
public class FriendshipStatusDto {
    private UUID userId;
    private String statusCode; // FRIEND, PENDING_INCOMING, PENDING_OUTGOING, BLOCKED, NONE
    private Friendship friendship; // can be null
}

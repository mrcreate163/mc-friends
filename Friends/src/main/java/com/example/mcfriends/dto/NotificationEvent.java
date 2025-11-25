package com.example.mcfriends.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class NotificationEvent {
    private final UUID userId;
    private final String message;
    private final String type = "FRIEND_ACCEPTED";
}
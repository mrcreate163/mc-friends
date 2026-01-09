package com.example.mcfriends.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;
    private UUID recipientId;
    private UUID senderId;
}
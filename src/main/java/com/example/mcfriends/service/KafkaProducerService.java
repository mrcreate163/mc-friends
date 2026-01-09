package com.example.mcfriends.service;

import com.example.mcfriends.dto.NotificationEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class KafkaProducerService {

    private static final String NOTIFICATION_TOPIC = "NOTIFICATION";
    private static final String ACCOUNT_CHANGES_TOPIC = "ACCOUNT_CHANGES";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendNotification(UUID userId, String message) {
        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_ACCEPTED");
        event.setRecipientId(userId);
        event.setMessage(message);
        kafkaTemplate.send(NOTIFICATION_TOPIC, userId.toString(), event);
    }

    public void sendAccountChangesEvent(NotificationEvent event) {
        kafkaTemplate.send(ACCOUNT_CHANGES_TOPIC, event.getRecipientId().toString(), event);
    }
}
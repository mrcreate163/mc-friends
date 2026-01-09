package com.example.mcfriends.service;

import com.example.mcfriends.dto.NotificationEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class KafkaProducerService {

    private static final String NOTIFICATION_TOPIC = "ACCOUNT_CHANGES";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendNotification(NotificationEvent event) {
        kafkaTemplate.send(NOTIFICATION_TOPIC, event.getRecipientId().toString(), event);
    }
}
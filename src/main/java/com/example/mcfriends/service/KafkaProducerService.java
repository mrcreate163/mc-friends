package com.example.mcfriends.service;

import com.example.mcfriends.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String NOTIFICATION_TOPIC = "ACCOUNT_CHANGES";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправить событие изменения дружбы в Kafka
     *
     * @param event событие для отправки
     */
    public void sendNotification(NotificationEvent event) {
        try {
            String key = event.getRecipientId().toString();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(NOTIFICATION_TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Sent notification event: {} to partition: {}",
                            event.getType(),
                            result.getRecordMetadata().partition());
                } else {
                    log.error("Failed to send notification event: {}",
                            event.getType(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error sending Kafka notification", e);
            // НЕ бросаем исключение - Kafka не должна ломать основной флоу
        }
    }
}

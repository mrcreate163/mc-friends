package com.example.mcfriends.service;

import com.example.mcfriends.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Сервис для отправки событий в Apache Kafka.
 * 
 * <p>Отвечает за:</p>
 * <ul>
 *   <li>Отправку событий изменения дружеских связей в топик ACCOUNT_CHANGES</li>
 *   <li>Асинхронную обработку результатов отправки</li>
 *   <li>Логирование успешных и неуспешных отправок</li>
 * </ul>
 * 
 * <p><b>Важно:</b> Сервис не бросает исключения при ошибках Kafka,
 * чтобы не ломать основной флоу бизнес-логики. Все ошибки только логируются.</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private static final String NOTIFICATION_TOPIC = "ACCOUNT_CHANGES";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправить событие изменения дружбы в Kafka топик ACCOUNT_CHANGES.
     * 
     * <p>Событие отправляется асинхронно. Ключом сообщения является recipientId,
     * что обеспечивает порядок обработки событий для одного пользователя.</p>
     * 
     * <p><b>Типы событий:</b></p>
     * <ul>
     *   <li>FRIEND_REQUEST_SENT - отправлена заявка</li>
     *   <li>FRIEND_REQUEST_ACCEPTED - заявка принята</li>
     *   <li>FRIEND_REQUEST_DECLINED - заявка отклонена</li>
     *   <li>FRIEND_BLOCKED - пользователь заблокирован</li>
     *   <li>FRIEND_UNBLOCKED - пользователь разблокирован</li>
     *   <li>FRIEND_SUBSCRIBED - подписка на пользователя</li>
     * </ul>
     * 
     * <p><b>Побочные эффекты:</b></p>
     * <ul>
     *   <li>Отправляет сообщение в Kafka топик ACCOUNT_CHANGES</li>
     *   <li>Логирует результат отправки (успех/ошибка)</li>
     *   <li>НЕ бросает исключения - ошибки только логируются</li>
     * </ul>
     * 
     * @param event событие для отправки, содержащее тип, recipientId и senderId
     */
    public void sendNotification(NotificationEvent event) {
        log.debug("Sending Kafka notification: eventType={}, recipientId={}, senderId={}", 
                event.getType(), event.getRecipientId(), event.getSenderId());
        
        try {
            String key = event.getRecipientId().toString();

            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(NOTIFICATION_TOPIC, key, event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka notification sent: eventType={}, partition={}, offset={}", 
                            event.getType(), 
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send Kafka notification: eventType={}, recipientId={}", 
                            event.getType(), event.getRecipientId(), ex);
                }
            });

        } catch (Exception e) {
            log.error("Error preparing Kafka message: eventType={}, recipientId={}", 
                    event.getType(), event.getRecipientId(), e);
            // НЕ бросаем исключение - Kafka не должна ломать основной флоу
        }
    }
}

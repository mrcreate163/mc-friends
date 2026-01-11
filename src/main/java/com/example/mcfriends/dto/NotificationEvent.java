package com.example.mcfriends.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.UUID;

/**
 * Data Transfer Object для событий уведомлений в Kafka.
 * 
 * <p>Используется для отправки событий о изменении дружеских связей
 * в топик ACCOUNT_CHANGES. Другие сервисы подписываются на этот топик
 * для получения уведомлений о событиях.</p>
 * 
 * <p>Типы событий:</p>
 * <ul>
 *   <li>FRIEND_REQUEST_SENT - отправлена заявка в друзья</li>
 *   <li>FRIEND_REQUEST_ACCEPTED - заявка принята</li>
 *   <li>FRIEND_REQUEST_DECLINED - заявка отклонена</li>
 *   <li>FRIEND_BLOCKED - пользователь заблокирован</li>
 *   <li>FRIEND_UNBLOCKED - пользователь разблокирован</li>
 *   <li>FRIEND_SUBSCRIBED - подписка на пользователя</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    
    /**
     * Тип события уведомления.
     * Определяет какое действие произошло с дружеской связью.
     */
    private String type;
    
    /**
     * UUID получателя уведомления.
     * Пользователь, которому будет отправлено уведомление.
     */
    private UUID recipientId;
    
    /**
     * UUID отправителя события.
     * Пользователь, инициировавший действие.
     */
    private UUID senderId;
}
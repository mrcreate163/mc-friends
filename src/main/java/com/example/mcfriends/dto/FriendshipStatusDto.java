package com.example.mcfriends.dto;

import com.example.mcfriends.model.Friendship;
import lombok.Data;

import java.util.UUID;

/**
 * Data Transfer Object для передачи статуса дружеской связи с пользователем.
 * 
 * <p>Используется для определения текущего состояния отношений между
 * текущим пользователем и целевым пользователем.</p>
 * 
 * <p>Возможные значения statusCode:</p>
 * <ul>
 *   <li>FRIEND - пользователи являются друзьями (ACCEPTED)</li>
 *   <li>PENDING_INCOMING - входящая заявка (текущий пользователь - target)</li>
 *   <li>PENDING_OUTGOING - исходящая заявка (текущий пользователь - initiator)</li>
 *   <li>BLOCKED - пользователь заблокирован</li>
 *   <li>SUBSCRIBED - односторонняя подписка</li>
 *   <li>NONE - нет связи между пользователями</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Data
public class FriendshipStatusDto {
    
    /**
     * UUID пользователя, статус связи с которым запрашивается.
     */
    private UUID userId;
    
    /**
     * Код статуса дружеской связи.
     * Определяет тип и направление связи относительно текущего пользователя.
     * Возможные значения: FRIEND, PENDING_INCOMING, PENDING_OUTGOING, BLOCKED, SUBSCRIBED, NONE.
     */
    private String statusCode;
    
    /**
     * Объект дружеской связи, если она существует.
     * Может быть null, если связи нет (statusCode = NONE).
     */
    private Friendship friendship;
}

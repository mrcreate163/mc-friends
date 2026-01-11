package com.example.mcfriends.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object для простых текстовых сообщений в ответах API.
 * 
 * <p>Используется для возврата успешных результатов операций,
 * когда не требуется возвращать полный объект сущности.</p>
 * 
 * <p>Примеры использования:</p>
 * <ul>
 *   <li>Подтверждение блокировки пользователя</li>
 *   <li>Подтверждение разблокировки пользователя</li>
 *   <li>Подтверждение подписки на пользователя</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    
    /**
     * Текстовое сообщение для клиента.
     * Обычно содержит подтверждение успешного выполнения операции.
     */
    private String message;
}

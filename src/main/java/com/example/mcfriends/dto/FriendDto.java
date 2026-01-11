package com.example.mcfriends.dto;

import com.example.mcfriends.model.FriendshipStatus;
import lombok.Data;

/**
 * Data Transfer Object для передачи информации о друге или заявке в друзья.
 * 
 * <p>Используется в REST API для:</p>
 * <ul>
 *   <li>Возврата списка друзей с полной информацией об аккаунтах</li>
 *   <li>Возврата входящих заявок в друзья</li>
 *   <li>Отображения статуса дружеской связи</li>
 * </ul>
 * 
 * <p>Объединяет данные аккаунта пользователя из account-service
 * с информацией о статусе дружбы из текущего сервиса.</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Data
public class FriendDto {
    
    /**
     * Информация об аккаунте друга или пользователя.
     * Получается из account-service через Feign Client.
     */
    private AccountDto account;
    
    /**
     * Статус дружеской связи с данным пользователем.
     * 
     * @see FriendshipStatus
     */
    private FriendshipStatus status;
}
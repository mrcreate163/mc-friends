package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое когда запрошенная сущность не найдена в БД.
 * 
 * <p>Используется для:</p>
 * <ul>
 *   <li>Отсутствия Friendship по ID</li>
 *   <li>Отсутствия пользователя в системе</li>
 *   <li>Отсутствия блокировки при попытке разблокировки</li>
 * </ul>
 * 
 * <p>HTTP mapping: 404 Not Found</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Создаёт новое исключение с указанным сообщением.
     * 
     * @param message текст ошибки для пользователя
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
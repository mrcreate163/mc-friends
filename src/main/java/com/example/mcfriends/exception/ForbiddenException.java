package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое при попытке выполнить запрещённую операцию.
 * 
 * <p>Используется для контроля доступа к операциям:</p>
 * <ul>
 *   <li>Попытка принять чужую заявку в друзья</li>
 *   <li>Попытка отклонить чужую заявку</li>
 *   <li>Попытка разблокировать пользователя, которого блокировал кто-то другой</li>
 * </ul>
 * 
 * <p>HTTP mapping: 403 Forbidden</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class ForbiddenException extends RuntimeException {
    
    /**
     * Создаёт новое исключение с указанным сообщением.
     * 
     * @param message текст ошибки, объясняющий почему операция запрещена
     */
    public ForbiddenException(String message) {
        super(message);
    }
}

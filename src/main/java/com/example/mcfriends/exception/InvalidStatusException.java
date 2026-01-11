package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое при попытке изменить статус заявки, которая уже обработана.
 * 
 * <p>Используется для валидации операций:</p>
 * <ul>
 *   <li>Попытка принять уже принятую или отклонённую заявку</li>
 *   <li>Попытка отклонить уже обработанную заявку</li>
 * </ul>
 * 
 * <p>HTTP mapping: 400 Bad Request</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class InvalidStatusException extends RuntimeException {
    
    /**
     * Создаёт новое исключение с указанным сообщением.
     * 
     * @param message текст ошибки, описывающий проблему со статусом
     */
    public InvalidStatusException(String message) {
        super(message);
    }
}

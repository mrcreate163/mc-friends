package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое когда дружеская связь уже существует.
 * 
 * <p>Используется для предотвращения дублирования связей:</p>
 * <ul>
 *   <li>Повторная отправка заявки в друзья</li>
 *   <li>Отправка заявки когда пользователи уже друзья</li>
 *   <li>Отправка заявки заблокированному пользователю</li>
 *   <li>Создание подписки когда уже есть связь</li>
 * </ul>
 * 
 * <p>HTTP mapping: 409 Conflict</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class FriendshipAlreadyExistsException extends RuntimeException {
    
    /**
     * Создаёт новое исключение с указанным сообщением.
     * 
     * @param message текст ошибки, описывающий какая именно связь уже существует
     */
    public FriendshipAlreadyExistsException(String message) {
        super(message);
    }
}

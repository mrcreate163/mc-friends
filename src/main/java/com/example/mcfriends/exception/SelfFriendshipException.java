package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое при попытке создать дружескую связь с самим собой.
 * 
 * <p>Используется для валидации операций:</p>
 * <ul>
 *   <li>Отправка заявки в друзья самому себе</li>
 *   <li>Блокировка самого себя</li>
 *   <li>Подписка на самого себя</li>
 * </ul>
 * 
 * <p>HTTP mapping: 400 Bad Request</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class SelfFriendshipException extends RuntimeException {
    
    /**
     * Создаёт новое исключение с предустановленным сообщением.
     * Сообщение: "Cannot send friend request to yourself"
     */
    public SelfFriendshipException() {
        super("Cannot send friend request to yourself");
    }
}

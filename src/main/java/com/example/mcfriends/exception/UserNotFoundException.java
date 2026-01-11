package com.example.mcfriends.exception;

/**
 * Исключение, выбрасываемое когда пользователь не найден в системе.
 * 
 * <p>Используется при обращении к несуществующему пользователю:</p>
 * <ul>
 *   <li>Отправка заявки в друзья несуществующему пользователю</li>
 *   <li>Попытка получить информацию о несуществующем пользователе</li>
 * </ul>
 * 
 * <p>HTTP mapping: 404 Not Found</p>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public class UserNotFoundException extends RuntimeException {
    
    /**
     * Создаёт новое исключение с указанным сообщением.
     * 
     * @param message текст ошибки с указанием ID пользователя
     */
    public UserNotFoundException(String message) {
        super(message);
    }
}

package com.example.mcfriends.model;

/**
 * Перечисление возможных статусов дружеской связи между пользователями.
 * 
 * <p>Жизненный цикл статусов:</p>
 * <pre>
 *     PENDING → ACCEPTED (принята)
 *     PENDING → DECLINED (отклонена)
 *     ACCEPTED → DELETED (удалена)
 *     ANY → BLOCKED (заблокирована)
 * </pre>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
public enum FriendshipStatus {
    
    /**
     * Ожидает принятия - заявка отправлена, но ещё не обработана.
     * Initiator отправил заявку, target должен принять или отклонить.
     */
    PENDING,
    
    /**
     * Принята - пользователи являются друзьями.
     * Target принял заявку от initiator.
     */
    ACCEPTED,
    
    /**
     * Заблокирована - один пользователь заблокировал другого.
     * Initiator заблокировал target. Target не может отправлять заявки initiator.
     */
    BLOCKED,
    
    /**
     * Отклонена - заявка отклонена получателем.
     * Target отклонил заявку от initiator.
     */
    DECLINED,
    
    /**
     * Подписка - односторонняя связь без заявки в друзья.
     * Позволяет видеть публичные посты пользователя.
     */
    SUBSCRIBED
}
package com.example.mcfriends.model;

/**
 * Перечисление типов фильтрации дружеских связей для API запросов.
 *
 * <p>Используется как параметр statusCode в GET /api/v1/friends для фильтрации
 * результатов по типу отношений с пользователем.</p>
 *
 * <p><b>Маппинг на FriendshipStatus:</b></p>
 * <ul>
 * <li>REQUEST_FROM → PENDING (где current user = initiator)</li>
 * <li>REQUEST_TO → PENDING (где current user = target)</li>
 * <li>FRIEND → ACCEPTED</li>
 * <li>BLOCKED → BLOCKED (где current user = initiator)</li>
 * <li>SUBSCRIBED → SUBSCRIBED (где current user = initiator)</li>
 * <li>WATCHING → SUBSCRIBED (где current user = target)</li>
 * </ul>
 *
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-12
 */
public enum FriendshipFilterStatus {
    /**
     * Исходящие заявки в друзья.
     * Текущий пользователь отправил заявку (initiator), ожидает принятия (PENDING).
     */
    REQUEST_FROM,

    /**
     * Входящие заявки в друзья.
     * Текущему пользователю прислали заявку (target), нужно принять или отклонить (PENDING).
     */
    REQUEST_TO,

    /**
     * Подтверждённые друзья.
     * Заявка принята обеими сторонами (ACCEPTED).
     */
    FRIEND,

    /**
     * Заблокированные пользователи.
     * Текущий пользователь заблокировал других (initiator, BLOCKED).
     */
    BLOCKED,

    /**
     * Подписки на пользователей.
     * Текущий пользователь подписан на других (initiator, SUBSCRIBED).
     */
    SUBSCRIBED,

    /**
     * Подписчики.
     * Другие пользователи подписаны на текущего (target, SUBSCRIBED).
     */
    WATCHING
}

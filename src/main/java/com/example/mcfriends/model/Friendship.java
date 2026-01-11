package com.example.mcfriends.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Сущность дружеской связи между двумя пользователями.
 * 
 * <p>Представляет запись в таблице friendships, описывающую отношения
 * между пользователями: дружбу, заявку в друзья, блокировку или подписку.</p>
 * 
 * <p>Основные сценарии использования:</p>
 * <ul>
 *   <li>Отправка и обработка заявок в друзья (PENDING → ACCEPTED/DECLINED)</li>
 *   <li>Управление списком друзей (ACCEPTED)</li>
 *   <li>Блокировка пользователей (BLOCKED)</li>
 *   <li>Подписка на публичные посты (SUBSCRIBED)</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Entity
@Table(name = "friendships")
@Data
public class Friendship {

    /**
     * Уникальный идентификатор записи дружбы.
     * Генерируется автоматически при создании записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * UUID пользователя, инициировавшего связь.
     * Для заявок в друзья - отправитель, для блокировки - блокирующий.
     */
    @Column(name = "user_id_initiator", nullable = false)
    private UUID userIdInitiator;

    /**
     * UUID пользователя, являющегося целью связи.
     * Для заявок в друзья - получатель, для блокировки - заблокированный.
     */
    @Column(name = "user_id_target", nullable = false)
    private UUID userIdTarget;

    /**
     * Текущий статус дружеской связи.
     * 
     * @see FriendshipStatus
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FriendshipStatus status;

    /**
     * Дата и время создания записи.
     * Устанавливается автоматически при создании сущности.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Дата и время последнего обновления записи.
     * Обновляется при изменении статуса связи.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
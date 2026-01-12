package com.example.mcfriends.repository;

import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA репозиторий для работы с сущностью {@link Friendship}.
 * 
 * <p>Содержит кастомные запросы для:</p>
 * <ul>
 *   <li>Поиска дружеских связей между пользователями</li>
 *   <li>Фильтрации по статусам (PENDING, ACCEPTED, BLOCKED)</li>
 *   <li>Подсчёта количества друзей</li>
 *   <li>Получения списков ID друзей для интеграции с другими сервисами</li>
 * </ul>
 * 
 * @author mc-friends Team
 * @version 1.0
 * @since 2026-01-11
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /**
     * Найти все дружеские связи пользователя с указанным статусом в обоих направлениях.
     * 
     * <p>Ищет записи где пользователь является либо initiator, либо target
     * и статус соответствует одному из указанных.</p>
     * 
     * @param userId первый ID пользователя для проверки initiator
     * @param status1 первый статус для фильтрации
     * @param userId2 второй ID пользователя для проверки target
     * @param status2 второй статус для фильтрации (обычно тот же что и status1)
     * @return список найденных связей
     */
    List<Friendship> findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
            UUID userId, FriendshipStatus status1, UUID userId2, FriendshipStatus status2
    );

    /**
     * Найти связи где пользователь является инициатором с указанным статусом.
     *
     * <p>Используется для получения:
     * <ul>
     * <li>Исходящих заявок (status = PENDING)</li>
     * <li>Заблокированных пользователей (status = BLOCKED)</li>
     * <li>Подписок (status = SUBSCRIBED)</li>
     * </ul>
     * </p>
     *
     * @param userIdInitiator UUID пользователя-инициатора
     * @param status статус для фильтрации
     * @param pageable параметры пагинации
     * @return страница с результатами
     */
    Page<Friendship> findByUserIdInitiatorAndStatus(
            UUID userIdInitiator,
            FriendshipStatus status,
            Pageable pageable);


    /**
     * Найти все дружеские связи пользователя с указанным статусом (постраничный результат).
     * 
     * <p>Ищет записи где:</p>
     * <ul>
     *   <li>f.status = :status1 И</li>
     *   <li>(f.userIdInitiator = :userId ИЛИ f.userIdTarget = :userId)</li>
     * </ul>
     * 
     * <p>Используется для получения списка друзей с пагинацией.</p>
     * 
     * @param userId UUID пользователя
     * @param status1 статус для фильтрации (обычно ACCEPTED)
     * @param pageable параметры пагинации (номер страницы, размер, сортировка)
     * @return страница с результатами
     */
    @Query("SELECT f FROM Friendship f WHERE " +
           "f.status = :status1 AND " +
           "(f.userIdInitiator = :userId OR f.userIdTarget = :userId)")
    Page<Friendship> findByUserIdAndStatus(@Param("userId") UUID userId, 
                                           @Param("status1") FriendshipStatus status1,
                                           Pageable pageable);

    /**
     * Найти дружескую связь между двумя конкретными пользователями (односторонняя).
     * 
     * <p>Ищет запись где initiatorId является инициатором, а targetId - целью.
     * Для двусторонней проверки используйте {@link #findByUserIds(UUID, UUID)}.</p>
     * 
     * @param initiatorId UUID пользователя-инициатора
     * @param targetId UUID пользователя-цели
     * @return Optional с Friendship если найдена, иначе Optional.empty()
     */
    Optional<Friendship> findByUserIdInitiatorAndUserIdTarget(UUID initiatorId, UUID targetId);

    /**
     * Найти дружескую связь между двумя пользователями в любом направлении.
     * 
     * <p>Ищет запись где:</p>
     * <ul>
     *   <li>(f.userIdInitiator = :userId1 И f.userIdTarget = :userId2) ИЛИ</li>
     *   <li>(f.userIdInitiator = :userId2 И f.userIdTarget = :userId1)</li>
     * </ul>
     * 
     * <p>Используется для проверки наличия любой связи между пользователями,
     * независимо от направления.</p>
     * 
     * @param userId1 UUID первого пользователя
     * @param userId2 UUID второго пользователя
     * @return Optional с Friendship если связь найдена, иначе Optional.empty()
     */
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userIdInitiator = :userId1 AND f.userIdTarget = :userId2) OR " +
           "(f.userIdInitiator = :userId2 AND f.userIdTarget = :userId1)")
    Optional<Friendship> findByUserIds(@Param("userId1") UUID userId1, 
                                       @Param("userId2") UUID userId2);

    /**
     * Найти все входящие заявки для пользователя с указанным статусом (постраничный результат).
     * 
     * <p>Ищет записи где пользователь является target (получателем) и статус соответствует указанному.
     * Обычно используется для получения входящих заявок в друзья (status = PENDING).</p>
     * 
     * @param userIdTarget UUID пользователя-получателя
     * @param status статус для фильтрации (обычно PENDING)
     * @param pageable параметры пагинации
     * @return страница с результатами
     */
    Page<Friendship> findByUserIdTargetAndStatus(UUID userIdTarget, FriendshipStatus status, Pageable pageable);

    /**
     * Подсчитать количество дружеских связей пользователя с указанным статусом.
     * 
     * <p>Считает записи где:</p>
     * <ul>
     *   <li>f.status = :status И</li>
     *   <li>(f.userIdInitiator = :userId ИЛИ f.userIdTarget = :userId)</li>
     * </ul>
     * 
     * <p>Используется для получения счётчика друзей пользователя.</p>
     * 
     * @param userId UUID пользователя
     * @param status статус для фильтрации (обычно ACCEPTED)
     * @return количество связей
     */
    @Query("SELECT COUNT(f) FROM Friendship f WHERE " +
           "f.status = :status AND " +
           "(f.userIdInitiator = :userId OR f.userIdTarget = :userId)")
    Long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") FriendshipStatus status);

    /**
     * Найти блокировку конкретного пользователя конкретным инициатором.
     * 
     * <p>Используется для операций разблокировки, чтобы убедиться что
     * текущий пользователь является инициатором блокировки.</p>
     * 
     * @param initiatorId UUID пользователя-инициатора блокировки
     * @param targetId UUID заблокированного пользователя
     * @param status статус (обычно BLOCKED)
     * @return Optional с Friendship если найдена, иначе Optional.empty()
     */
    Optional<Friendship> findByUserIdInitiatorAndUserIdTargetAndStatus(
            UUID initiatorId, UUID targetId, FriendshipStatus status
    );

    /**
     * Получить список UUID всех друзей пользователя.
     * 
     * <p>Возвращает ID других пользователей в дружеских связях.
     * Использует CASE выражение для определения ID друга:</p>
     * <ul>
     *   <li>Если userId = initiator, возвращает target</li>
     *   <li>Если userId = target, возвращает initiator</li>
     * </ul>
     * 
     * <p>Используется для интеграции с другими сервисами, которым нужны
     * только ID друзей без дополнительной информации.</p>
     * 
     * @param userId UUID пользователя
     * @param status статус для фильтрации (обычно ACCEPTED)
     * @return список UUID друзей
     */
    @Query("SELECT CASE WHEN f.userIdInitiator = :userId THEN f.userIdTarget " +
           "ELSE f.userIdInitiator END " +
           "FROM Friendship f WHERE f.status = :status AND " +
           "(f.userIdInitiator = :userId OR f.userIdTarget = :userId)")
    List<UUID> findFriendIdsByUserId(@Param("userId") UUID userId,
                                      @Param("status") FriendshipStatus status);

    /**
     * Получить список UUID всех заблокированных пользователей, где текущий пользователь - инициатор.
     * 
     * <p>Возвращает только target ID, где пользователь является инициатором блокировки.
     * Не включает пользователей, которые заблокировали текущего пользователя.</p>
     * 
     * <p>Используется для получения списка заблокированных пользователей для UI
     * или для проверки прав доступа.</p>
     * 
     * @param userId UUID пользователя-инициатора
     * @param status статус (обычно BLOCKED)
     * @return список UUID заблокированных пользователей
     */
    @Query("SELECT f.userIdTarget FROM Friendship f WHERE " +
           "f.status = :status AND f.userIdInitiator = :userId")
    List<UUID> findBlockedUserIdsByInitiator(@Param("userId") UUID userId,
                                              @Param("status") FriendshipStatus status);
}
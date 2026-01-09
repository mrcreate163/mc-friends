package com.example.mcfriends.repository;

import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    List<Friendship> findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
            UUID userId, FriendshipStatus status1, UUID userId2, FriendshipStatus status2
    );

    Optional<Friendship> findByUserIdInitiatorAndUserIdTarget(UUID initiatorId, UUID targetId);

    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.userIdInitiator = :userId1 AND f.userIdTarget = :userId2) OR " +
           "(f.userIdInitiator = :userId2 AND f.userIdTarget = :userId1)")
    Optional<Friendship> findByUserIds(@Param("userId1") UUID userId1, 
                                       @Param("userId2") UUID userId2);
}
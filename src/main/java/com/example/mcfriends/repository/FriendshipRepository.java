package com.example.mcfriends.repository;

import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    List<Friendship> findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
            UUID userId, FriendshipStatus status1, UUID userId2, FriendshipStatus status2
    );

    Optional<Friendship> findByUserIdInitiatorAndUserIdTarget(UUID initiatorId, UUID targetId);

    Page<Friendship> findByUserIdTargetAndStatus(UUID userIdTarget, FriendshipStatus status, Pageable pageable);
}
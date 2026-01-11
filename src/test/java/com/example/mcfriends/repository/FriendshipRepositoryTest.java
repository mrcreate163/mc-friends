package com.example.mcfriends.repository;

import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for FriendshipRepository
 * Tests custom JPQL queries and repository methods
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("FriendshipRepository Integration Tests")
class FriendshipRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FriendshipRepository friendshipRepository;

    private UUID user1Id;
    private UUID user2Id;
    private UUID user3Id;

    @BeforeEach
    void setUp() {
        user1Id = UUID.randomUUID();
        user2Id = UUID.randomUUID();
        user3Id = UUID.randomUUID();
        
        // Clean up any existing data
        friendshipRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Should find friendship by initiator and target")
    void findByUserIdInitiatorAndUserIdTarget_ReturnsResult_WhenExists() {
        // Arrange
        Friendship friendship = createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.PENDING);

        // Act
        Optional<Friendship> result = friendshipRepository.findByUserIdInitiatorAndUserIdTarget(user1Id, user2Id);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getUserIdInitiator()).isEqualTo(user1Id);
        assertThat(result.get().getUserIdTarget()).isEqualTo(user2Id);
    }

    @Test
    @DisplayName("Should return empty when friendship does not exist")
    void findByUserIdInitiatorAndUserIdTarget_ReturnsEmpty_WhenNotExists() {
        // Act
        Optional<Friendship> result = friendshipRepository.findByUserIdInitiatorAndUserIdTarget(user1Id, user2Id);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should find friendship by user IDs in both directions")
    void findByUserIds_ReturnsResult_WhenExistsInEitherDirection() {
        // Arrange
        Friendship friendship = createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);

        // Act - Test both directions
        Optional<Friendship> result1 = friendshipRepository.findByUserIds(user1Id, user2Id);
        Optional<Friendship> result2 = friendshipRepository.findByUserIds(user2Id, user1Id);

        // Assert
        assertThat(result1).isPresent();
        assertThat(result2).isPresent();
        assertThat(result1.get().getId()).isEqualTo(result2.get().getId());
    }

    @Test
    @DisplayName("Should find pending requests by target user and status")
    void findByUserIdTargetAndStatus_ReturnsPagedResults() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.PENDING);
        createAndPersistFriendship(user3Id, user2Id, FriendshipStatus.PENDING);
        createAndPersistFriendship(user1Id, user3Id, FriendshipStatus.ACCEPTED);
        
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Friendship> result = friendshipRepository.findByUserIdTargetAndStatus(
                user2Id, FriendshipStatus.PENDING, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should filter friendships correctly by status and target")
    void findByUserIdTargetAndStatus_FiltersCorrectly() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.PENDING);
        createAndPersistFriendship(user3Id, user2Id, FriendshipStatus.ACCEPTED);
        
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Friendship> pendingResults = friendshipRepository.findByUserIdTargetAndStatus(
                user2Id, FriendshipStatus.PENDING, pageable);
        Page<Friendship> acceptedResults = friendshipRepository.findByUserIdTargetAndStatus(
                user2Id, FriendshipStatus.ACCEPTED, pageable);

        // Assert
        assertThat(pendingResults.getContent()).hasSize(1);
        assertThat(acceptedResults.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should find friendships by userId and status")
    void findByUserIdAndStatus_ReturnsAllFriendships() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user1Id, user3Id, FriendshipStatus.PENDING);
        
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        Page<Friendship> result = friendshipRepository.findByUserIdAndStatus(
                user1Id, FriendshipStatus.ACCEPTED, pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Should count friendships by userId and status")
    void countByUserIdAndStatus_ReturnsCorrectCount() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user1Id, user3Id, FriendshipStatus.PENDING);

        // Act
        Long acceptedCount = friendshipRepository.countByUserIdAndStatus(user1Id, FriendshipStatus.ACCEPTED);
        Long pendingCount = friendshipRepository.countByUserIdAndStatus(user1Id, FriendshipStatus.PENDING);

        // Assert
        assertThat(acceptedCount).isEqualTo(2);
        assertThat(pendingCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should find friendship by initiator, target, and status")
    void findByUserIdInitiatorAndUserIdTargetAndStatus_ReturnsResult() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.BLOCKED);

        // Act
        Optional<Friendship> result = friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                user1Id, user2Id, FriendshipStatus.BLOCKED);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
    }

    @Test
    @DisplayName("Should find friend IDs for a user")
    void findFriendIdsByUserId_ReturnsCorrectIds() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user1Id, UUID.randomUUID(), FriendshipStatus.PENDING);

        // Act
        List<UUID> friendIds = friendshipRepository.findFriendIdsByUserId(user1Id, FriendshipStatus.ACCEPTED);

        // Assert
        assertThat(friendIds).hasSize(2);
        assertThat(friendIds).contains(user2Id, user3Id);
    }

    @Test
    @DisplayName("Should find blocked user IDs where current user is initiator")
    void findBlockedUserIdsByInitiator_ReturnsCorrectIds() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.BLOCKED);
        createAndPersistFriendship(user1Id, user3Id, FriendshipStatus.BLOCKED);
        createAndPersistFriendship(user2Id, user1Id, FriendshipStatus.BLOCKED); // Different initiator

        // Act
        List<UUID> blockedIds = friendshipRepository.findBlockedUserIdsByInitiator(user1Id, FriendshipStatus.BLOCKED);

        // Assert
        assertThat(blockedIds).hasSize(2);
        assertThat(blockedIds).contains(user2Id, user3Id);
        assertThat(blockedIds).doesNotContain(user1Id);
    }

    @Test
    @DisplayName("Should find friendships for both initiator and target")
    void findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus_ReturnsAllRelated() {
        // Arrange
        createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
        createAndPersistFriendship(user2Id, user3Id, FriendshipStatus.ACCEPTED); // Not related to user1

        // Act
        List<Friendship> friendships = friendshipRepository.findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                user1Id, FriendshipStatus.ACCEPTED,
                user1Id, FriendshipStatus.ACCEPTED);

        // Assert
        assertThat(friendships).hasSize(2);
        assertThat(friendships).allMatch(f -> 
            f.getUserIdInitiator().equals(user1Id) || f.getUserIdTarget().equals(user1Id)
        );
    }

    @Test
    @DisplayName("Should delete and persist changes")
    void deleteAndFlush_RemovesFriendship() {
        // Arrange
        Friendship friendship = createAndPersistFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
        UUID friendshipId = friendship.getId();

        // Act
        friendshipRepository.delete(friendship);
        entityManager.flush();
        entityManager.clear();

        // Assert
        Optional<Friendship> result = friendshipRepository.findById(friendshipId);
        assertThat(result).isEmpty();
    }

    // Helper method
    private Friendship createAndPersistFriendship(UUID initiatorId, UUID targetId, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setUserIdInitiator(initiatorId);
        friendship.setUserIdTarget(targetId);
        friendship.setStatus(status);
        friendship.setCreatedAt(LocalDateTime.now());
        
        Friendship saved = entityManager.persist(friendship);
        entityManager.flush();
        return saved;
    }
}

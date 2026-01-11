package com.example.mcfriends.repository;

import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
 * Tests custom queries with H2 in-memory database
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

        // Clear all data before each test
        friendshipRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
    }

    private Friendship createFriendship(UUID initiatorId, UUID targetId, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setUserIdInitiator(initiatorId);
        friendship.setUserIdTarget(targetId);
        friendship.setStatus(status);
        friendship.setCreatedAt(LocalDateTime.now());
        return entityManager.persist(friendship);
    }

    @Nested
    @DisplayName("findByUserIdInitiatorAndUserIdTarget Tests")
    class FindByUserIdInitiatorAndUserIdTargetTests {

        @Test
        @DisplayName("Returns result when exists")
        void findByUserIdInitiatorAndUserIdTarget_ReturnsResult_WhenExists() {
            // Arrange
            Friendship friendship = createFriendship(user1Id, user2Id, FriendshipStatus.PENDING);
            entityManager.flush();

            // Act
            Optional<Friendship> result = friendshipRepository
                    .findByUserIdInitiatorAndUserIdTarget(user1Id, user2Id);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getUserIdInitiator()).isEqualTo(user1Id);
            assertThat(result.get().getUserIdTarget()).isEqualTo(user2Id);
        }

        @Test
        @DisplayName("Returns empty when not exists")
        void findByUserIdInitiatorAndUserIdTarget_ReturnsEmpty_WhenNotExists() {
            // Act
            Optional<Friendship> result = friendshipRepository
                    .findByUserIdInitiatorAndUserIdTarget(user1Id, user2Id);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIds Tests")
    class FindByUserIdsTests {

        @Test
        @DisplayName("Returns result for both directions")
        void findByUserIds_ReturnsResult_ForBothDirections() {
            // Arrange
            Friendship friendship = createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            // Act - Check both directions
            Optional<Friendship> result1 = friendshipRepository.findByUserIds(user1Id, user2Id);
            Optional<Friendship> result2 = friendshipRepository.findByUserIds(user2Id, user1Id);

            // Assert
            assertThat(result1).isPresent();
            assertThat(result2).isPresent();
            assertThat(result1.get().getId()).isEqualTo(result2.get().getId());
        }

        @Test
        @DisplayName("Returns empty when not exists")
        void findByUserIds_ReturnsEmpty_WhenNotExists() {
            // Act
            Optional<Friendship> result = friendshipRepository.findByUserIds(user1Id, user2Id);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdTargetAndStatus Tests")
    class FindByUserIdTargetAndStatusTests {

        @Test
        @DisplayName("Returns paged results")
        void findByUserIdTargetAndStatus_ReturnsPagedResults() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.PENDING);
            createFriendship(user3Id, user2Id, FriendshipStatus.PENDING);
            createFriendship(user1Id, user3Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Friendship> result = friendshipRepository
                    .findByUserIdTargetAndStatus(user2Id, FriendshipStatus.PENDING, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                    .allMatch(f -> f.getUserIdTarget().equals(user2Id))
                    .allMatch(f -> f.getStatus() == FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("Filters correctly by status")
        void findByUserIdTargetAndStatus_FiltersCorrectly() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.PENDING);
            createFriendship(user3Id, user2Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Friendship> pendingResult = friendshipRepository
                    .findByUserIdTargetAndStatus(user2Id, FriendshipStatus.PENDING, pageable);
            Page<Friendship> acceptedResult = friendshipRepository
                    .findByUserIdTargetAndStatus(user2Id, FriendshipStatus.ACCEPTED, pageable);

            // Assert
            assertThat(pendingResult.getContent()).hasSize(1);
            assertThat(acceptedResult.getContent()).hasSize(1);
            assertThat(pendingResult.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(acceptedResult.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("findByUserIdAndStatus Tests")
    class FindByUserIdAndStatusTests {

        @Test
        @DisplayName("Returns all friendships for user as initiator or target")
        void findByUserIdAndStatus_ReturnsAllFriendships() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            createFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
            createFriendship(user2Id, user3Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Friendship> result = friendshipRepository
                    .findByUserIdAndStatus(user1Id, FriendshipStatus.ACCEPTED, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent()).allMatch(f -> 
                f.getUserIdInitiator().equals(user1Id) || f.getUserIdTarget().equals(user1Id)
            );
        }

        @Test
        @DisplayName("Works for both directions")
        void findByUserIdAndStatus_WorksForBothDirections() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            createFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<Friendship> result = friendshipRepository
                    .findByUserIdAndStatus(user1Id, FriendshipStatus.ACCEPTED, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(2);
            boolean hasAsInitiator = result.getContent().stream()
                    .anyMatch(f -> f.getUserIdInitiator().equals(user1Id));
            boolean hasAsTarget = result.getContent().stream()
                    .anyMatch(f -> f.getUserIdTarget().equals(user1Id));
            assertThat(hasAsInitiator).isTrue();
            assertThat(hasAsTarget).isTrue();
        }
    }

    @Nested
    @DisplayName("countByUserIdAndStatus Tests")
    class CountByUserIdAndStatusTests {

        @Test
        @DisplayName("Returns correct count")
        void countByUserIdAndStatus_ReturnsCorrectCount() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            createFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
            createFriendship(user1Id, user3Id, FriendshipStatus.PENDING);
            entityManager.flush();

            // Act
            Long count = friendshipRepository.countByUserIdAndStatus(user1Id, FriendshipStatus.ACCEPTED);

            // Assert
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("Returns zero when no matches")
        void countByUserIdAndStatus_ReturnsZero_WhenNoMatches() {
            // Act
            Long count = friendshipRepository.countByUserIdAndStatus(user1Id, FriendshipStatus.ACCEPTED);

            // Assert
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus Tests")
    class FindByUserIdInitiatorAndStatusOrUserIdTargetAndStatusTests {

        @Test
        @DisplayName("Returns friendships from both directions")
        void findByUserIdInitiatorAndStatus_ReturnsBothDirections() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            createFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
            createFriendship(user2Id, user3Id, FriendshipStatus.PENDING);
            entityManager.flush();

            // Act
            List<Friendship> result = friendshipRepository
                    .findByUserIdInitiatorAndStatusOrUserIdTargetAndStatus(
                            user1Id, FriendshipStatus.ACCEPTED,
                            user1Id, FriendshipStatus.ACCEPTED
                    );

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(f -> f.getStatus() == FriendshipStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("findByUserIdInitiatorAndUserIdTargetAndStatus Tests")
    class FindByUserIdInitiatorAndUserIdTargetAndStatusTests {

        @Test
        @DisplayName("Returns result when exists with matching status")
        void findByUserIdInitiatorAndUserIdTargetAndStatus_ReturnsResult() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.BLOCKED);
            entityManager.flush();

            // Act
            Optional<Friendship> result = friendshipRepository
                    .findByUserIdInitiatorAndUserIdTargetAndStatus(
                            user1Id, user2Id, FriendshipStatus.BLOCKED
                    );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        }

        @Test
        @DisplayName("Returns empty when status doesn't match")
        void findByUserIdInitiatorAndUserIdTargetAndStatus_ReturnsEmpty_WhenStatusDifferent() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            // Act
            Optional<Friendship> result = friendshipRepository
                    .findByUserIdInitiatorAndUserIdTargetAndStatus(
                            user1Id, user2Id, FriendshipStatus.BLOCKED
                    );

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findFriendIdsByUserId Tests")
    class FindFriendIdsByUserIdTests {

        @Test
        @DisplayName("Returns friend IDs for both initiator and target")
        void findFriendIdsByUserId_ReturnsAllFriendIds() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            createFriendship(user3Id, user1Id, FriendshipStatus.ACCEPTED);
            createFriendship(user1Id, UUID.randomUUID(), FriendshipStatus.PENDING);
            entityManager.flush();

            // Act
            List<UUID> friendIds = friendshipRepository
                    .findFriendIdsByUserId(user1Id, FriendshipStatus.ACCEPTED);

            // Assert
            assertThat(friendIds).hasSize(2);
            assertThat(friendIds).contains(user2Id, user3Id);
            assertThat(friendIds).doesNotContain(user1Id);
        }
    }

    @Nested
    @DisplayName("findBlockedUserIdsByInitiator Tests")
    class FindBlockedUserIdsByInitiatorTests {

        @Test
        @DisplayName("Returns blocked user IDs where current user is initiator")
        void findBlockedUserIdsByInitiator_ReturnsBlockedIds() {
            // Arrange
            createFriendship(user1Id, user2Id, FriendshipStatus.BLOCKED);
            createFriendship(user1Id, user3Id, FriendshipStatus.BLOCKED);
            createFriendship(user2Id, user1Id, FriendshipStatus.BLOCKED); // Should not be included
            entityManager.flush();

            // Act
            List<UUID> blockedIds = friendshipRepository
                    .findBlockedUserIdsByInitiator(user1Id, FriendshipStatus.BLOCKED);

            // Assert
            assertThat(blockedIds).hasSize(2);
            assertThat(blockedIds).contains(user2Id, user3Id);
            assertThat(blockedIds).doesNotContain(user1Id);
        }
    }

    @Nested
    @DisplayName("Delete Operations Tests")
    class DeleteOperationsTests {

        @Test
        @DisplayName("Delete removes friendship correctly")
        void delete_RemovesFriendshipCorrectly() {
            // Arrange
            Friendship friendship = createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();
            UUID friendshipId = friendship.getId();

            // Act
            friendshipRepository.delete(friendship);
            entityManager.flush();

            // Assert
            Optional<Friendship> result = friendshipRepository.findById(friendshipId);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Delete does not affect other records")
        void delete_DoesNotAffectOtherRecords() {
            // Arrange
            Friendship friendship1 = createFriendship(user1Id, user2Id, FriendshipStatus.ACCEPTED);
            Friendship friendship2 = createFriendship(user1Id, user3Id, FriendshipStatus.ACCEPTED);
            entityManager.flush();

            // Act
            friendshipRepository.delete(friendship1);
            entityManager.flush();

            // Assert
            Optional<Friendship> result = friendshipRepository.findById(friendship2.getId());
            assertThat(result).isPresent();
        }
    }
}

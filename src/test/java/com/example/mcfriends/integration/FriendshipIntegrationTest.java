package com.example.mcfriends.integration;

import com.example.mcfriends.client.AccountClient;
import com.example.mcfriends.client.dto.UserDataDetails;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.repository.FriendshipRepository;
import com.example.mcfriends.service.FriendshipService;
import com.example.mcfriends.service.KafkaProducerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * End-to-End Integration Tests
 * Tests complete user flows with real database and service layer
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"ACCOUNT_CHANGES"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Friendship Integration Tests")
class FriendshipIntegrationTest {

    @Autowired
    private FriendshipService friendshipService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private KafkaProducerService kafkaProducerService;

    private UUID userA;
    private UUID userB;
    private UUID userC;

    @BeforeEach
    void setUp() {
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        userC = UUID.randomUUID();

        friendshipRepository.deleteAll();

        // Mock account client responses
        setupAccountClientMocks();
    }

    private void setupAccountClientMocks() {
        AccountDto accountA = new AccountDto();
        accountA.setId(userA);
        accountA.setUsername("userA");

        AccountDto accountB = new AccountDto();
        accountB.setId(userB);
        accountB.setUsername("userB");

        AccountDto accountC = new AccountDto();
        accountC.setId(userC);
        accountC.setUsername("userC");

        when(accountClient.getAccountsByIds(anyList()))
                .thenAnswer(invocation -> {
                    List<UUID> ids = invocation.getArgument(0);
                    List<AccountDto> accounts = new ArrayList<>();
                    if (ids.contains(userA)) accounts.add(accountA);
                    if (ids.contains(userB)) accounts.add(accountB);
                    if (ids.contains(userC)) accounts.add(accountC);
                    return accounts;
                });
    }

    @Test
    @DisplayName("Full friendship flow - Send request and approve")
    @Transactional
    void fullFriendshipFlow_SendRequestAndApprove_Success() {
        // 1. User A sends friend request to User B
        Friendship request = friendshipService.sendFriendRequest(userA, userB);
        assertThat(request).isNotNull();
        assertThat(request.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        assertThat(request.getUserIdInitiator()).isEqualTo(userA);
        assertThat(request.getUserIdTarget()).isEqualTo(userB);

        // 2. User B sees the request in incoming requests
        Page<FriendDto> incomingRequests = friendshipService.getIncomingRequests(userB, PageRequest.of(0, 10));
        assertThat(incomingRequests.getContent()).hasSize(1);
        assertThat(incomingRequests.getContent().get(0).getAccount().getId()).isEqualTo(userA);
        assertThat(incomingRequests.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);

        // 3. User B accepts the request
        Friendship accepted = friendshipService.acceptFriendRequest(request.getId(), userB);
        assertThat(accepted.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);

        // 4. Both users see each other in friends list
        Page<FriendDto> friendsA = friendshipService.getAcceptedFriendsDetails(userA, PageRequest.of(0, 10));
        Page<FriendDto> friendsB = friendshipService.getAcceptedFriendsDetails(userB, PageRequest.of(0, 10));

        assertThat(friendsA.getContent()).hasSize(1);
        assertThat(friendsA.getContent().get(0).getAccount().getId()).isEqualTo(userB);

        assertThat(friendsB.getContent()).hasSize(1);
        assertThat(friendsB.getContent().get(0).getAccount().getId()).isEqualTo(userA);

        // Verify friend counts
        assertThat(friendshipService.getFriendCount(userA)).isEqualTo(1L);
        assertThat(friendshipService.getFriendCount(userB)).isEqualTo(1L);

        // 5. User A removes friend
        friendshipService.deleteFriendship(userA, userB);

        // 6. Friendship no longer exists
        assertThat(friendshipService.getFriendCount(userA)).isZero();
        assertThat(friendshipService.getFriendCount(userB)).isZero();
    }

    @Test
    @DisplayName("Full friendship flow - Send request and decline")
    @Transactional
    void fullFriendshipFlow_SendRequestAndDecline_Success() {
        // 1. User A sends friend request to User B
        Friendship request = friendshipService.sendFriendRequest(userA, userB);
        assertThat(request.getStatus()).isEqualTo(FriendshipStatus.PENDING);

        // 2. User B sees the request
        Page<FriendDto> incomingRequests = friendshipService.getIncomingRequests(userB, PageRequest.of(0, 10));
        assertThat(incomingRequests.getContent()).hasSize(1);

        // 3. User B declines the request
        Friendship declined = friendshipService.declineFriendRequest(request.getId(), userB);
        assertThat(declined.getStatus()).isEqualTo(FriendshipStatus.DECLINED);

        // 4. Users are not friends
        assertThat(friendshipService.getFriendCount(userA)).isZero();
        assertThat(friendshipService.getFriendCount(userB)).isZero();

        // 5. User B no longer sees the request in incoming requests
        Page<FriendDto> incomingAfterDecline = friendshipService.getIncomingRequests(userB, PageRequest.of(0, 10));
        assertThat(incomingAfterDecline.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Block flow - Block user and verify restrictions")
    @Transactional
    void blockFlow_BlockUserAndVerifyRestrictions() {
        // 1. User A blocks User B
        friendshipService.blockUser(userA, userB);

        // Verify block was created
        Optional<Friendship> block = friendshipRepository
                .findByUserIdInitiatorAndUserIdTargetAndStatus(userA, userB, FriendshipStatus.BLOCKED);
        assertThat(block).isPresent();
        assertThat(block.get().getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

        // 2. User B cannot send friend request to User A (relationship already exists as BLOCKED)
        assertThatThrownBy(() -> friendshipService.sendFriendRequest(userB, userA))
                .hasMessageContaining("blocked");

        // 3. User A unblocks User B
        friendshipService.unblockUser(userA, userB);

        // Verify block was removed
        Optional<Friendship> afterUnblock = friendshipRepository
                .findByUserIdInitiatorAndUserIdTargetAndStatus(userA, userB, FriendshipStatus.BLOCKED);
        assertThat(afterUnblock).isEmpty();

        // 4. User B can now send friend request
        Friendship request = friendshipService.sendFriendRequest(userB, userA);
        assertThat(request).isNotNull();
        assertThat(request.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    @DisplayName("Multiple friendships - User has multiple friends")
    @Transactional
    void multipleFriendships_UserHasMultipleFriends() {
        // User A sends requests to User B and User C
        Friendship requestB = friendshipService.sendFriendRequest(userA, userB);
        Friendship requestC = friendshipService.sendFriendRequest(userA, userC);

        // Both accept
        friendshipService.acceptFriendRequest(requestB.getId(), userB);
        friendshipService.acceptFriendRequest(requestC.getId(), userC);

        // User A has 2 friends
        Long friendCount = friendshipService.getFriendCount(userA);
        assertThat(friendCount).isEqualTo(2L);

        Page<FriendDto> friendsA = friendshipService.getAcceptedFriendsDetails(userA, PageRequest.of(0, 10));
        assertThat(friendsA.getContent()).hasSize(2);

        List<UUID> friendIds = friendshipService.getFriendIds(userA);
        assertThat(friendIds).containsExactlyInAnyOrder(userB, userC);
    }

    @Test
    @DisplayName("Friendship status - Returns correct status for different relationships")
    @Transactional
    void friendshipStatus_ReturnsCorrectStatusForDifferentRelationships() {
        // No relationship
        var statusNone = friendshipService.getFriendshipStatus(userA, userB);
        assertThat(statusNone.getStatusCode()).isEqualTo("NONE");

        // Pending outgoing
        Friendship request = friendshipService.sendFriendRequest(userA, userB);
        var statusPendingOut = friendshipService.getFriendshipStatus(userA, userB);
        assertThat(statusPendingOut.getStatusCode()).isEqualTo("PENDING_OUTGOING");

        // Pending incoming
        var statusPendingIn = friendshipService.getFriendshipStatus(userB, userA);
        assertThat(statusPendingIn.getStatusCode()).isEqualTo("PENDING_INCOMING");

        // Accepted
        friendshipService.acceptFriendRequest(request.getId(), userB);
        var statusAccepted = friendshipService.getFriendshipStatus(userA, userB);
        assertThat(statusAccepted.getStatusCode()).isEqualTo("FRIEND");

        // After removal
        friendshipService.deleteFriendship(userA, userB);
        var statusAfterRemoval = friendshipService.getFriendshipStatus(userA, userB);
        assertThat(statusAfterRemoval.getStatusCode()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("Block existing friend - Updates friendship to blocked")
    @Transactional
    void blockExistingFriend_UpdatesFriendshipToBlocked() {
        // Become friends first
        Friendship request = friendshipService.sendFriendRequest(userA, userB);
        friendshipService.acceptFriendRequest(request.getId(), userB);

        assertThat(friendshipService.getFriendCount(userA)).isEqualTo(1L);

        // User A blocks User B
        friendshipService.blockUser(userA, userB);

        // Verify friendship is now blocked
        Optional<Friendship> blocked = friendshipRepository.findByUserIds(userA, userB);
        assertThat(blocked).isPresent();
        assertThat(blocked.get().getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

        // Friend count should be 0
        assertThat(friendshipService.getFriendCount(userA)).isZero();
    }

    @Test
    @DisplayName("Get blocked user IDs - Returns correct list")
    @Transactional
    void getBlockedUserIds_ReturnsCorrectList() {
        // User A blocks User B and User C
        friendshipService.blockUser(userA, userB);
        friendshipService.blockUser(userA, userC);

        // Get blocked IDs
        List<UUID> blockedIds = friendshipService.getBlockedUserIds(userA);

        assertThat(blockedIds).hasSize(2);
        assertThat(blockedIds).containsExactlyInAnyOrder(userB, userC);
    }

    @Test
    @DisplayName("Subscribe to user - Creates subscription relationship")
    @Transactional
    void subscribeToUser_CreatesSubscriptionRelationship() {
        // User A subscribes to User B
        friendshipService.subscribeToUser(userA, userB);

        // Verify subscription exists
        Optional<Friendship> subscription = friendshipRepository.findByUserIds(userA, userB);
        assertThat(subscription).isPresent();
        assertThat(subscription.get().getStatus()).isEqualTo(FriendshipStatus.SUBSCRIBED);
        assertThat(subscription.get().getUserIdInitiator()).isEqualTo(userA);
        assertThat(subscription.get().getUserIdTarget()).isEqualTo(userB);

        // Verify friendship status
        var status = friendshipService.getFriendshipStatus(userA, userB);
        assertThat(status.getStatusCode()).isEqualTo("SUBSCRIBED");
    }
}

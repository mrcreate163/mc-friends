package com.example.mcfriends.service;

import com.example.mcfriends.client.AccountClient;
import com.example.mcfriends.dto.AccountDto;
import com.example.mcfriends.dto.FriendDto;
import com.example.mcfriends.dto.FriendshipStatusDto;
import com.example.mcfriends.dto.NotificationEvent;
import com.example.mcfriends.exception.ForbiddenException;
import com.example.mcfriends.exception.FriendshipAlreadyExistsException;
import com.example.mcfriends.exception.InvalidStatusException;
import com.example.mcfriends.exception.ResourceNotFoundException;
import com.example.mcfriends.exception.SelfFriendshipException;
import com.example.mcfriends.model.Friendship;
import com.example.mcfriends.model.FriendshipStatus;
import com.example.mcfriends.repository.FriendshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FriendshipService
 * Tests all service layer methods with mocked dependencies
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FriendshipService Unit Tests")
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private AccountClient accountClient;

    @InjectMocks
    private FriendshipService friendshipService;

    private UUID initiatorId;
    private UUID targetId;
    private UUID thirdUserId;
    private Friendship friendship;

    @BeforeEach
    void setUp() {
        initiatorId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        thirdUserId = UUID.randomUUID();

        friendship = new Friendship();
        friendship.setId(UUID.randomUUID());
        friendship.setUserIdInitiator(initiatorId);
        friendship.setUserIdTarget(targetId);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("sendFriendRequest Tests")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Success - When no existing relationship")
        void sendFriendRequest_Success_WhenNoExistingRelationship() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            Friendship result = friendshipService.sendFriendRequest(initiatorId, targetId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);

            ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(captor.capture());

            Friendship saved = captor.getValue();
            assertThat(saved.getUserIdInitiator()).isEqualTo(initiatorId);
            assertThat(saved.getUserIdTarget()).isEqualTo(targetId);
            assertThat(saved.getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("Throws Exception - When already friends")
        void sendFriendRequest_ThrowsException_WhenAlreadyFriends() {
            // Arrange
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("already friends");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws Exception - When request already sent")
        void sendFriendRequest_ThrowsException_WhenRequestAlreadySent() {
            // Arrange
            friendship.setStatus(FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("pending");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws Exception - When user blocked by target")
        void sendFriendRequest_ThrowsException_WhenUserBlockedByTarget() {
            // Arrange
            friendship.setStatus(FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("blocked");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws Exception - When self request")
        void sendFriendRequest_ThrowsException_WhenSelfRequest() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, initiatorId))
                    .isInstanceOf(SelfFriendshipException.class);

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Success - When previous request was declined")
        void sendFriendRequest_Success_WhenPreviousRequestDeclined() {
            // Arrange
            friendship.setStatus(FriendshipStatus.DECLINED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            Friendship result = friendshipService.sendFriendRequest(initiatorId, targetId);

            // Assert
            assertThat(result).isNotNull();
            verify(friendshipRepository).save(any(Friendship.class));
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest Tests")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Success - When request exists")
        void acceptFriendRequest_Success_WhenRequestExists() {
            // Arrange
            UUID requestId = friendship.getId();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            Friendship result = friendshipService.acceptFriendRequest(requestId, targetId);

            // Assert
            assertThat(result).isNotNull();
            verify(friendshipRepository).save(friendship);
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());

            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_REQUEST_ACCEPTED");
            assertThat(event.getRecipientId()).isEqualTo(initiatorId);
            assertThat(event.getSenderId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Throws Exception - When request not found")
        void acceptFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найден");

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Throws Exception - When user is not target")
        void acceptFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            UUID requestId = friendship.getId();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, thirdUserId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Доступ запрещен");

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Throws Exception - When request already accepted")
        void acceptFriendRequest_ThrowsException_WhenRequestAlreadyAccepted() {
            // Arrange
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            UUID requestId = friendship.getId();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, targetId))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("обработан");

            verify(friendshipRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("declineFriendRequest Tests")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Success - When request exists")
        void declineFriendRequest_Success_WhenRequestExists() {
            // Arrange
            UUID requestId = friendship.getId();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            Friendship result = friendshipService.declineFriendRequest(requestId, targetId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            verify(friendshipRepository).save(friendship);

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());

            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_REQUEST_DECLINED");
            assertThat(event.getRecipientId()).isEqualTo(initiatorId);
            assertThat(event.getSenderId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Throws Exception - When request not found")
        void declineFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(requestId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throws Exception - When user is not target")
        void declineFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            UUID requestId = friendship.getId();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(friendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(requestId, thirdUserId))
                    .isInstanceOf(ForbiddenException.class);

            verify(friendshipRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getIncomingRequests Tests")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Returns paged results")
        void getIncomingRequests_ReturnsPagedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Friendship> friendships = Collections.singletonList(friendship);
            Page<Friendship> page = new PageImpl<>(friendships, pageable, 1);

            AccountDto accountDto = new AccountDto();
            accountDto.setId(initiatorId);
            accountDto.setUsername("testUser");

            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(page);
            when(accountClient.getAccountsByIds(anyList())).thenReturn(Collections.singletonList(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(initiatorId);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("Returns empty page when no requests")
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> emptyPage = Page.empty(pageable);

            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            assertThat(result).isEmpty();
            verify(accountClient, never()).getAccountsByIds(anyList());
        }

        @Test
        @DisplayName("Filters only pending status")
        void getIncomingRequests_FiltersOnlyPendingStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> page = Page.empty(pageable);

            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(page);

            // Act
            friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            verify(friendshipRepository).findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable);
        }
    }

    @Nested
    @DisplayName("getFriends Tests")
    class GetFriendsTests {

        @Test
        @DisplayName("Returns paged friends")
        void getAcceptedFriendsDetails_ReturnsPagedFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            List<Friendship> friendships = Collections.singletonList(friendship);
            Page<Friendship> page = new PageImpl<>(friendships, pageable, 1);

            AccountDto accountDto = new AccountDto();
            accountDto.setId(targetId);
            accountDto.setUsername("friendUser");

            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(page);
            when(accountClient.getAccountsByIds(anyList())).thenReturn(Collections.singletonList(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Returns empty page when no friends")
        void getAcceptedFriendsDetails_ReturnsEmptyPage_WhenNoFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> emptyPage = Page.empty(pageable);

            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            assertThat(result).isEmpty();
            verify(accountClient, never()).getAccountsByIds(anyList());
        }
    }

    @Nested
    @DisplayName("removeFriend Tests")
    class RemoveFriendTests {

        @Test
        @DisplayName("Success - When friendship exists")
        void removeFriend_Success_WhenFriendshipExists() {
            // Arrange
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            friendshipService.deleteFriendship(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("Throws Exception - When friendship not found")
        void removeFriend_ThrowsException_WhenFriendshipNotFound() {
            // Arrange
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(any(), any()))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.deleteFriendship(initiatorId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(friendshipRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getFriendsCount Tests")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Returns correct count")
        void getFriendsCount_ReturnsCorrectCount() {
            // Arrange
            when(friendshipRepository.countByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED))
                    .thenReturn(5L);

            // Act
            Long count = friendshipService.getFriendCount(initiatorId);

            // Assert
            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("Returns zero when no friends")
        void getFriendsCount_ReturnsZero_WhenNoFriends() {
            // Arrange
            when(friendshipRepository.countByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED))
                    .thenReturn(0L);

            // Act
            Long count = friendshipService.getFriendCount(initiatorId);

            // Assert
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("getFriendshipStatus Tests")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Returns FRIEND when accepted")
        void getFriendshipStatus_ReturnsAccepted_WhenFriends() {
            // Arrange
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("FRIEND");
            assertThat(result.getUserId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Returns PENDING_OUTGOING when request sent")
        void getFriendshipStatus_ReturnsPending_WhenRequestSent() {
            // Arrange
            friendship.setStatus(FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("PENDING_OUTGOING");
        }

        @Test
        @DisplayName("Returns BLOCKED when blocked")
        void getFriendshipStatus_ReturnsBlocked_WhenBlocked() {
            // Arrange
            friendship.setStatus(FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("Returns NONE when no relationship")
        void getFriendshipStatus_ReturnsNone_WhenNoRelationship() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.empty());

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("NONE");
            assertThat(result.getFriendship()).isNull();
        }
    }

    @Nested
    @DisplayName("blockUser Tests")
    class BlockUserTests {

        @Test
        @DisplayName("Success - When no existing block")
        void blockUser_Success_WhenNoExistingBlock() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            friendshipService.blockUser(initiatorId, targetId);

            // Assert
            ArgumentCaptor<Friendship> captor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(captor.capture());

            Friendship blocked = captor.getValue();
            assertThat(blocked.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
            assertThat(blocked.getUserIdInitiator()).isEqualTo(initiatorId);
            assertThat(blocked.getUserIdTarget()).isEqualTo(targetId);

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo("FRIEND_BLOCKED");
        }

        @Test
        @DisplayName("Updates existing relationship when friends")
        void blockUser_UpdatesExistingRelationship_WhenFriends() {
            // Arrange
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.of(friendship));
            when(friendshipRepository.save(any(Friendship.class))).thenReturn(friendship);

            // Act
            friendshipService.blockUser(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).save(friendship);
            assertThat(friendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
        }

        @Test
        @DisplayName("Throws Exception - When self block")
        void blockUser_ThrowsException_WhenSelfBlock() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.blockUser(initiatorId, initiatorId))
                    .isInstanceOf(SelfFriendshipException.class);

            verify(friendshipRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("unblockUser Tests")
    class UnblockUserTests {

        @Test
        @DisplayName("Success - When block exists")
        void unblockUser_Success_WhenBlockExists() {
            // Arrange
            friendship.setStatus(FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    initiatorId, targetId, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.of(friendship));

            // Act
            friendshipService.unblockUser(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).delete(friendship);

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getType()).isEqualTo("FRIEND_UNBLOCKED");
        }

        @Test
        @DisplayName("Throws Exception - When no block found")
        void unblockUser_ThrowsException_WhenNoBlockFound() {
            // Arrange
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    initiatorId, targetId, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.unblockUser(initiatorId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найдена");

            verify(friendshipRepository, never()).delete(any());
        }
    }
}

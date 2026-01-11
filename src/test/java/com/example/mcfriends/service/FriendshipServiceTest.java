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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FriendshipService
 * Tests business logic for friendship operations
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

    @BeforeEach
    void setUp() {
        initiatorId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        targetId = UUID.fromString("223e4567-e89b-12d3-a456-426614174001");
        thirdUserId = UUID.fromString("323e4567-e89b-12d3-a456-426614174002");
    }

    @Nested
    @DisplayName("sendFriendRequest Tests")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Should send friend request successfully when no existing relationship")
        void sendFriendRequest_Success_WhenNoExistingRelationship() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship f = invocation.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            // Act
            Friendship result = friendshipService.sendFriendRequest(initiatorId, targetId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getUserIdInitiator()).isEqualTo(initiatorId);
            assertThat(result.getUserIdTarget()).isEqualTo(targetId);
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("Should throw exception when users are already friends")
        void sendFriendRequest_ThrowsException_WhenAlreadyFriends() {
            // Arrange
            Friendship existingFriendship = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(existingFriendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("already friends");
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when request already sent")
        void sendFriendRequest_ThrowsException_WhenRequestAlreadySent() {
            // Arrange
            Friendship existingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(existingRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("already pending");
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when user is blocked")
        void sendFriendRequest_ThrowsException_WhenUserBlockedByTarget() {
            // Arrange
            Friendship blockedFriendship = createFriendship(targetId, initiatorId, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(blockedFriendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, targetId))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("blocked");
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when sending friend request to self")
        void sendFriendRequest_ThrowsException_WhenSelfRequest() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(initiatorId, initiatorId))
                    .isInstanceOf(SelfFriendshipException.class);
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow new request when previous was declined")
        void sendFriendRequest_Success_WhenPreviousRequestDeclined() {
            // Arrange
            Friendship declinedFriendship = createFriendship(initiatorId, targetId, FriendshipStatus.DECLINED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(declinedFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship f = invocation.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            // Act
            Friendship result = friendshipService.sendFriendRequest(initiatorId, targetId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("Should allow upgrade from subscription to friend request")
        void sendFriendRequest_Success_WhenUpgradingFromSubscription() {
            // Arrange
            Friendship subscription = createFriendship(initiatorId, targetId, FriendshipStatus.SUBSCRIBED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(subscription));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship f = invocation.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            // Act
            Friendship result = friendshipService.sendFriendRequest(initiatorId, targetId);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            verify(friendshipRepository).save(any(Friendship.class));
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest Tests")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Should accept friend request successfully when request exists")
        void approveFriendRequest_Success_WhenRequestExists() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.acceptFriendRequest(requestId, targetId);

            // Assert
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(result.getUpdatedAt()).isNotNull();
            
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getType()).isEqualTo("FRIEND_REQUEST_ACCEPTED");
            assertThat(capturedEvent.getRecipientId()).isEqualTo(initiatorId);
            assertThat(capturedEvent.getSenderId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Should throw exception when request not found")
        void approveFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найден");
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should throw exception when user is not target")
        void approveFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, thirdUserId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Доступ запрещен");
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when request already accepted")
        void approveFriendRequest_ThrowsException_WhenRequestAlreadyAccepted() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship acceptedRequest = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            acceptedRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(acceptedRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(requestId, targetId))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("уже обработан");
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should send Kafka event after successful approval")
        void approveFriendRequest_SendsKafkaEvent_AfterApproval() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.acceptFriendRequest(requestId, targetId);

            // Assert
            verify(kafkaProducerService, times(1)).sendNotification(any(NotificationEvent.class));
        }
    }

    @Nested
    @DisplayName("declineFriendRequest Tests")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Should decline friend request successfully when request exists")
        void declineFriendRequest_Success_WhenRequestExists() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.declineFriendRequest(requestId, targetId);

            // Assert
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(result.getUpdatedAt()).isNotNull();
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("Should throw exception when request not found")
        void declineFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(requestId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should throw exception when user is not target")
        void declineFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(requestId, thirdUserId))
                    .isInstanceOf(ForbiddenException.class);
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should send Kafka event after declining")
        void declineFriendRequest_SendsKafkaEvent_AfterDeclining() {
            // Arrange
            UUID requestId = UUID.randomUUID();
            Friendship pendingRequest = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            pendingRequest.setId(requestId);
            
            when(friendshipRepository.findById(requestId)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.declineFriendRequest(requestId, targetId);

            // Assert
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getType()).isEqualTo("FRIEND_REQUEST_DECLINED");
            assertThat(capturedEvent.getRecipientId()).isEqualTo(initiatorId);
            assertThat(capturedEvent.getSenderId()).isEqualTo(targetId);
        }
    }

    @Nested
    @DisplayName("getIncomingRequests Tests")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Should return paged results for incoming requests")
        void getIncomingRequests_ReturnsPagedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship request1 = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            Friendship request2 = createFriendship(thirdUserId, targetId, FriendshipStatus.PENDING);
            
            Page<Friendship> friendshipPage = new PageImpl<>(List.of(request1, request2), pageable, 2);
            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(friendshipPage);
            
            AccountDto account1 = createAccountDto(initiatorId, "User 1");
            AccountDto account2 = createAccountDto(thirdUserId, "User 2");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(account1, account2));

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
        }

        @Test
        @DisplayName("Should return empty page when no requests")
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should filter only PENDING status")
        void getIncomingRequests_FiltersOnlyPendingStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> friendshipPage = Page.empty(pageable);
            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(friendshipPage);

            // Act
            friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            verify(friendshipRepository).findByUserIdTargetAndStatus(
                    eq(targetId), eq(FriendshipStatus.PENDING), eq(pageable)
            );
        }

        @Test
        @DisplayName("Should return correct user as target")
        void getIncomingRequests_ReturnsCorrectUserAsTarget() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship request = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            
            Page<Friendship> friendshipPage = new PageImpl<>(List.of(request), pageable, 1);
            when(friendshipRepository.findByUserIdTargetAndStatus(targetId, FriendshipStatus.PENDING, pageable))
                    .thenReturn(friendshipPage);
            
            AccountDto account = createAccountDto(initiatorId, "Initiator User");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(account));

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(targetId, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(initiatorId);
        }
    }

    @Nested
    @DisplayName("getFriends Tests")
    class GetFriendsTests {

        @Test
        @DisplayName("Should return paged friends")
        void getFriends_ReturnsPagedFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship friendship1 = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            Friendship friendship2 = createFriendship(targetId, thirdUserId, FriendshipStatus.ACCEPTED);
            
            Page<Friendship> friendshipPage = new PageImpl<>(List.of(friendship1, friendship2), pageable, 2);
            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(friendshipPage);
            
            AccountDto account1 = createAccountDto(targetId, "Friend 1");
            AccountDto account2 = createAccountDto(thirdUserId, "Friend 2");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(account1, account2));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return empty page when no friends")
        void getFriends_ReturnsEmptyPage_WhenNoFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should filter only ACCEPTED status")
        void getFriends_FiltersOnlyAcceptedStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> friendshipPage = Page.empty(pageable);
            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(friendshipPage);

            // Act
            friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            verify(friendshipRepository).findByUserIdAndStatus(
                    eq(initiatorId), eq(FriendshipStatus.ACCEPTED), eq(pageable)
            );
        }

        @Test
        @DisplayName("Should return correct friend data")
        void getFriends_ReturnsCorrectFriendData() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship friendship = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            
            Page<Friendship> friendshipPage = new PageImpl<>(List.of(friendship), pageable, 1);
            when(friendshipRepository.findByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(friendshipPage);
            
            AccountDto account = createAccountDto(targetId, "Friend User");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(account));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(initiatorId, pageable);

            // Assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(targetId);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        }
    }

    @Nested
    @DisplayName("removeFriend Tests")
    class RemoveFriendTests {

        @Test
        @DisplayName("Should remove friend successfully when friendship exists")
        void removeFriend_Success_WhenFriendshipExists() {
            // Arrange
            Friendship friendship = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));
            doNothing().when(friendshipRepository).delete(any(Friendship.class));

            // Act
            friendshipService.deleteFriendship(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("Should throw exception when friendship not found")
        void removeFriend_ThrowsException_WhenFriendshipNotFound() {
            // Arrange
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(initiatorId, targetId))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(targetId, initiatorId))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.deleteFriendship(initiatorId, targetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найдена");
            verify(friendshipRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should find friendship in both directions")
        void removeFriend_FindsInBothDirections() {
            // Arrange
            Friendship friendship = createFriendship(targetId, initiatorId, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(initiatorId, targetId))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(targetId, initiatorId))
                    .thenReturn(Optional.of(friendship));

            // Act
            friendshipService.deleteFriendship(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).delete(friendship);
        }
    }

    @Nested
    @DisplayName("getFriendsCount Tests")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Should return correct count of friends")
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
        @DisplayName("Should return zero when no friends")
        void getFriendsCount_ReturnsZero_WhenNoFriends() {
            // Arrange
            when(friendshipRepository.countByUserIdAndStatus(initiatorId, FriendshipStatus.ACCEPTED))
                    .thenReturn(0L);

            // Act
            Long count = friendshipService.getFriendCount(initiatorId);

            // Assert
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getFriendshipStatus Tests")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Should return FRIEND status when users are friends")
        void getFriendshipStatus_ReturnsAccepted_WhenFriends() {
            // Arrange
            Friendship friendship = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("FRIEND");
            assertThat(result.getUserId()).isEqualTo(targetId);
        }

        @Test
        @DisplayName("Should return PENDING_OUTGOING when request sent by current user")
        void getFriendshipStatus_ReturnsPending_WhenRequestSent() {
            // Arrange
            Friendship friendship = createFriendship(initiatorId, targetId, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("PENDING_OUTGOING");
        }

        @Test
        @DisplayName("Should return PENDING_INCOMING when request received")
        void getFriendshipStatus_ReturnsPendingIncoming_WhenRequestReceived() {
            // Arrange
            Friendship friendship = createFriendship(targetId, initiatorId, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("PENDING_INCOMING");
        }

        @Test
        @DisplayName("Should return BLOCKED when user is blocked")
        void getFriendshipStatus_ReturnsBlocked_WhenBlocked() {
            // Arrange
            Friendship friendship = createFriendship(initiatorId, targetId, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(initiatorId, targetId);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("Should return NONE when no relationship exists")
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
        @DisplayName("Should block user successfully when no existing block")
        void blockUser_Success_WhenNoExistingBlock() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship f = invocation.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            // Act
            friendshipService.blockUser(initiatorId, targetId);

            // Assert
            ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(friendshipCaptor.capture());
            
            Friendship savedFriendship = friendshipCaptor.getValue();
            assertThat(savedFriendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
            assertThat(savedFriendship.getUserIdInitiator()).isEqualTo(initiatorId);
            assertThat(savedFriendship.getUserIdTarget()).isEqualTo(targetId);
            
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("Should update existing relationship to BLOCKED when friends")
        void blockUser_UpdatesExistingRelationship_WhenFriends() {
            // Arrange
            Friendship existingFriendship = createFriendship(initiatorId, targetId, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.of(existingFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.blockUser(initiatorId, targetId);

            // Assert
            assertThat(existingFriendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
            assertThat(existingFriendship.getUserIdInitiator()).isEqualTo(initiatorId);
            verify(friendshipRepository).save(existingFriendship);
        }

        @Test
        @DisplayName("Should throw exception when trying to block self")
        void blockUser_ThrowsException_WhenSelfBlock() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.blockUser(initiatorId, initiatorId))
                    .isInstanceOf(SelfFriendshipException.class);
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should send Kafka event after blocking")
        void blockUser_SendsKafkaEvent_AfterBlocking() {
            // Arrange
            when(friendshipRepository.findByUserIds(initiatorId, targetId))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> {
                Friendship f = invocation.getArgument(0);
                f.setId(UUID.randomUUID());
                return f;
            });

            // Act
            friendshipService.blockUser(initiatorId, targetId);

            // Assert
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getType()).isEqualTo("FRIEND_BLOCKED");
            assertThat(capturedEvent.getRecipientId()).isEqualTo(targetId);
            assertThat(capturedEvent.getSenderId()).isEqualTo(initiatorId);
        }
    }

    @Nested
    @DisplayName("unblockUser Tests")
    class UnblockUserTests {

        @Test
        @DisplayName("Should unblock user successfully when block exists")
        void unblockUser_Success_WhenBlockExists() {
            // Arrange
            Friendship blockedFriendship = createFriendship(initiatorId, targetId, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    initiatorId, targetId, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.of(blockedFriendship));
            doNothing().when(friendshipRepository).delete(any(Friendship.class));

            // Act
            friendshipService.unblockUser(initiatorId, targetId);

            // Assert
            verify(friendshipRepository).delete(blockedFriendship);
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("Should throw exception when no block found")
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

        @Test
        @DisplayName("Should send Kafka event after unblocking")
        void unblockUser_SendsKafkaEvent_AfterUnblocking() {
            // Arrange
            Friendship blockedFriendship = createFriendship(initiatorId, targetId, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    initiatorId, targetId, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.of(blockedFriendship));

            // Act
            friendshipService.unblockUser(initiatorId, targetId);

            // Assert
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent capturedEvent = eventCaptor.getValue();
            assertThat(capturedEvent.getType()).isEqualTo("FRIEND_UNBLOCKED");
            assertThat(capturedEvent.getRecipientId()).isEqualTo(targetId);
            assertThat(capturedEvent.getSenderId()).isEqualTo(initiatorId);
        }
    }

    // Helper methods
    private Friendship createFriendship(UUID initiatorId, UUID targetId, FriendshipStatus status) {
        Friendship friendship = new Friendship();
        friendship.setId(UUID.randomUUID());
        friendship.setUserIdInitiator(initiatorId);
        friendship.setUserIdTarget(targetId);
        friendship.setStatus(status);
        friendship.setCreatedAt(LocalDateTime.now());
        return friendship;
    }

    private AccountDto createAccountDto(UUID id, String username) {
        AccountDto account = new AccountDto();
        account.setId(id);
        account.setUsername(username);
        return account;
    }
}

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
 * Unit tests for FriendshipService.
 * Tests all service methods with mocked dependencies.
 * Target coverage: 90%+
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

    private static final UUID USER_ID_1 = UUID.randomUUID();
    private static final UUID USER_ID_2 = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    @Nested
    @DisplayName("sendFriendRequest tests")
    class SendFriendRequestTests {

        @Test
        @DisplayName("Should successfully send friend request when no existing relationship")
        void sendFriendRequest_Success_WhenNoExistingRelationship() {
            // Arrange
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getUserIdInitiator()).isEqualTo(USER_ID_1);
            assertThat(result.getUserIdTarget()).isEqualTo(USER_ID_2);
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(result.getCreatedAt()).isNotNull();

            verify(friendshipRepository).findByUserIds(USER_ID_1, USER_ID_2);
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("Should throw exception when users are already friends")
        void sendFriendRequest_ThrowsException_WhenAlreadyFriends() {
            // Arrange
            Friendship existingFriendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(existingFriendship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("already friends");

            verify(friendshipRepository).findByUserIds(USER_ID_1, USER_ID_2);
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when friend request already sent")
        void sendFriendRequest_ThrowsException_WhenRequestAlreadySent() {
            // Arrange
            Friendship existingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(existingRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("already pending");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when user is blocked by target")
        void sendFriendRequest_ThrowsException_WhenUserBlockedByTarget() {
            // Arrange
            Friendship blockedRelationship = createFriendship(USER_ID_2, USER_ID_1, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(blockedRelationship));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2))
                    .isInstanceOf(FriendshipAlreadyExistsException.class)
                    .hasMessageContaining("blocked");

            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when trying to send friend request to self")
        void sendFriendRequest_ThrowsException_WhenSelfRequest() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.sendFriendRequest(USER_ID_1, USER_ID_1))
                    .isInstanceOf(SelfFriendshipException.class);

            verify(friendshipRepository, never()).findByUserIds(any(), any());
            verify(friendshipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow request after previous was declined")
        void sendFriendRequest_Success_AfterPreviousDeclined() {
            // Arrange
            Friendship declinedRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.DECLINED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(declinedRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            verify(friendshipRepository).save(any(Friendship.class));
        }

        @Test
        @DisplayName("Should allow upgrading subscription to friend request")
        void sendFriendRequest_Success_AfterSubscription() {
            // Arrange
            Friendship subscription = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.SUBSCRIBED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(subscription));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.sendFriendRequest(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.PENDING);
            verify(friendshipRepository).save(any(Friendship.class));
        }
    }

    @Nested
    @DisplayName("acceptFriendRequest tests")
    class AcceptFriendRequestTests {

        @Test
        @DisplayName("Should successfully accept friend request")
        void approveFriendRequest_Success_WhenRequestExists() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.acceptFriendRequest(REQUEST_ID, USER_ID_2);

            // Assert
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(result.getUpdatedAt()).isNotNull();

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_REQUEST_ACCEPTED");
            assertThat(event.getRecipientId()).isEqualTo(USER_ID_1);
            assertThat(event.getSenderId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("Should throw exception when request not found")
        void approveFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(REQUEST_ID, USER_ID_2))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найден");

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should throw exception when user is not target")
        void approveFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

            // Act & Assert - USER_ID_1 trying to accept, but they are initiator
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(REQUEST_ID, USER_ID_1))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Доступ запрещен");

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should throw exception when request already accepted")
        void approveFriendRequest_ThrowsException_WhenRequestAlreadyAccepted() {
            // Arrange
            Friendship acceptedRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            acceptedRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(acceptedRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.acceptFriendRequest(REQUEST_ID, USER_ID_2))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("уже обработан");

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should send Kafka event after approval")
        void approveFriendRequest_SendsKafkaEvent_AfterApproval() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.acceptFriendRequest(REQUEST_ID, USER_ID_2);

            // Assert
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
        }
    }

    @Nested
    @DisplayName("declineFriendRequest tests")
    class DeclineFriendRequestTests {

        @Test
        @DisplayName("Should successfully decline friend request")
        void declineFriendRequest_Success_WhenRequestExists() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            Friendship result = friendshipService.declineFriendRequest(REQUEST_ID, USER_ID_2);

            // Assert
            assertThat(result.getStatus()).isEqualTo(FriendshipStatus.DECLINED);
            assertThat(result.getUpdatedAt()).isNotNull();

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_REQUEST_DECLINED");
            assertThat(event.getRecipientId()).isEqualTo(USER_ID_1);
            assertThat(event.getSenderId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("Should throw exception when request not found")
        void declineFriendRequest_ThrowsException_WhenRequestNotFound() {
            // Arrange
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(REQUEST_ID, USER_ID_2))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should throw exception when user is not target")
        void declineFriendRequest_ThrowsException_WhenUserIsNotTarget() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.declineFriendRequest(REQUEST_ID, USER_ID_1))
                    .isInstanceOf(ForbiddenException.class);

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should send Kafka event after declining")
        void declineFriendRequest_SendsKafkaEvent_AfterDeclining() {
            // Arrange
            Friendship pendingRequest = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            pendingRequest.setId(REQUEST_ID);
            when(friendshipRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.declineFriendRequest(REQUEST_ID, USER_ID_2);

            // Assert
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
        }
    }

    @Nested
    @DisplayName("getIncomingRequests tests")
    class GetIncomingRequestsTests {

        @Test
        @DisplayName("Should return paged results of incoming requests")
        void getIncomingRequests_ReturnsPagedResults() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship request1 = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            Page<Friendship> friendshipsPage = new PageImpl<>(List.of(request1), pageable, 1);
            
            when(friendshipRepository.findByUserIdTargetAndStatus(USER_ID_2, FriendshipStatus.PENDING, pageable))
                    .thenReturn(friendshipsPage);
            
            AccountDto accountDto = createAccountDto(USER_ID_1, "user1@test.com", "User 1");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(USER_ID_2, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.PENDING);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(USER_ID_1);
        }

        @Test
        @DisplayName("Should return empty page when no requests")
        void getIncomingRequests_ReturnsEmptyPage_WhenNoRequests() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Friendship> emptyPage = Page.empty(pageable);
            when(friendshipRepository.findByUserIdTargetAndStatus(USER_ID_2, FriendshipStatus.PENDING, pageable))
                    .thenReturn(emptyPage);

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(USER_ID_2, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            verify(accountClient, never()).getAccountsByIds(any());
        }

        @Test
        @DisplayName("Should filter only PENDING status requests")
        void getIncomingRequests_FiltersOnlyPendingStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(friendshipRepository.findByUserIdTargetAndStatus(USER_ID_2, FriendshipStatus.PENDING, pageable))
                    .thenReturn(Page.empty(pageable));

            // Act
            friendshipService.getIncomingRequests(USER_ID_2, pageable);

            // Assert
            verify(friendshipRepository).findByUserIdTargetAndStatus(USER_ID_2, FriendshipStatus.PENDING, pageable);
        }

        @Test
        @DisplayName("Should return correct user as target")
        void getIncomingRequests_ReturnsCorrectUserAsTarget() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship request = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            Page<Friendship> friendshipsPage = new PageImpl<>(List.of(request), pageable, 1);
            
            when(friendshipRepository.findByUserIdTargetAndStatus(USER_ID_2, FriendshipStatus.PENDING, pageable))
                    .thenReturn(friendshipsPage);
            
            AccountDto accountDto = createAccountDto(USER_ID_1, "user1@test.com", "User 1");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getIncomingRequests(USER_ID_2, pageable);

            // Assert
            assertThat(request.getUserIdTarget()).isEqualTo(USER_ID_2);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(USER_ID_1);
        }
    }

    @Nested
    @DisplayName("getFriends tests")
    class GetFriendsTests {

        @Test
        @DisplayName("Should return paged list of friends")
        void getFriends_ReturnsPagedFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            Page<Friendship> friendshipsPage = new PageImpl<>(List.of(friendship), pageable, 1);
            
            when(friendshipRepository.findByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(friendshipsPage);
            
            AccountDto accountDto = createAccountDto(USER_ID_2, "user2@test.com", "User 2");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(USER_ID_1, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
            assertThat(result.getContent().get(0).getAccount().getId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("Should return empty page when no friends")
        void getFriends_ReturnsEmptyPage_WhenNoFriends() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(friendshipRepository.findByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(Page.empty(pageable));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(USER_ID_1, pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            verify(accountClient, never()).getAccountsByIds(any());
        }

        @Test
        @DisplayName("Should filter only ACCEPTED status")
        void getFriends_FiltersOnlyAcceptedStatus() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(friendshipRepository.findByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(Page.empty(pageable));

            // Act
            friendshipService.getAcceptedFriendsDetails(USER_ID_1, pageable);

            // Assert
            verify(friendshipRepository).findByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED, pageable);
        }

        @Test
        @DisplayName("Should return correct friend data")
        void getFriends_ReturnsCorrectFriendData() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            Page<Friendship> friendshipsPage = new PageImpl<>(List.of(friendship), pageable, 1);
            
            when(friendshipRepository.findByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED, pageable))
                    .thenReturn(friendshipsPage);
            
            AccountDto accountDto = createAccountDto(USER_ID_2, "user2@test.com", "User 2");
            when(accountClient.getAccountsByIds(any())).thenReturn(List.of(accountDto));

            // Act
            Page<FriendDto> result = friendshipService.getAcceptedFriendsDetails(USER_ID_1, pageable);

            // Assert
            FriendDto friendDto = result.getContent().get(0);
            assertThat(friendDto.getAccount().getUsername()).isEqualTo("User 2");
            assertThat(friendDto.getAccount().getId()).isEqualTo(USER_ID_2);
        }
    }

    @Nested
    @DisplayName("removeFriend tests")
    class RemoveFriendTests {

        @Test
        @DisplayName("Should successfully remove friend")
        void removeFriend_Success_WhenFriendshipExists() {
            // Arrange
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(USER_ID_1, USER_ID_2))
                    .thenReturn(Optional.of(friendship));

            // Act
            friendshipService.deleteFriendship(USER_ID_1, USER_ID_2);

            // Assert
            verify(friendshipRepository).delete(friendship);
        }

        @Test
        @DisplayName("Should throw exception when friendship not found")
        void removeFriend_ThrowsException_WhenFriendshipNotFound() {
            // Arrange
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(USER_ID_1, USER_ID_2))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(USER_ID_2, USER_ID_1))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.deleteFriendship(USER_ID_1, USER_ID_2))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найдена");

            verify(friendshipRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should work for both users in friendship")
        void removeFriend_Success_ForBothUsers() {
            // Arrange - USER_ID_2 initiated, but USER_ID_1 can also delete
            Friendship friendship = createFriendship(USER_ID_2, USER_ID_1, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(USER_ID_1, USER_ID_2))
                    .thenReturn(Optional.empty());
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTarget(USER_ID_2, USER_ID_1))
                    .thenReturn(Optional.of(friendship));

            // Act
            friendshipService.deleteFriendship(USER_ID_1, USER_ID_2);

            // Assert
            verify(friendshipRepository).delete(friendship);
        }
    }

    @Nested
    @DisplayName("getFriendsCount tests")
    class GetFriendsCountTests {

        @Test
        @DisplayName("Should return correct count of friends")
        void getFriendsCount_ReturnsCorrectCount() {
            // Arrange
            when(friendshipRepository.countByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED))
                    .thenReturn(5L);

            // Act
            Long count = friendshipService.getFriendCount(USER_ID_1);

            // Assert
            assertThat(count).isEqualTo(5L);
            verify(friendshipRepository).countByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED);
        }

        @Test
        @DisplayName("Should return zero when no friends")
        void getFriendsCount_ReturnsZero_WhenNoFriends() {
            // Arrange
            when(friendshipRepository.countByUserIdAndStatus(USER_ID_1, FriendshipStatus.ACCEPTED))
                    .thenReturn(0L);

            // Act
            Long count = friendshipService.getFriendCount(USER_ID_1);

            // Assert
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getFriendshipStatus tests")
    class GetFriendshipStatusTests {

        @Test
        @DisplayName("Should return ACCEPTED status when friends")
        void getFriendshipStatus_ReturnsAccepted_WhenFriends() {
            // Arrange
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("FRIEND");
            assertThat(result.getUserId()).isEqualTo(USER_ID_2);
            assertThat(result.getFriendship()).isNotNull();
        }

        @Test
        @DisplayName("Should return PENDING_OUTGOING when request sent by current user")
        void getFriendshipStatus_ReturnsPendingOutgoing_WhenRequestSent() {
            // Arrange
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("PENDING_OUTGOING");
            assertThat(result.getUserId()).isEqualTo(USER_ID_2);
        }

        @Test
        @DisplayName("Should return PENDING_INCOMING when request received")
        void getFriendshipStatus_ReturnsPendingIncoming_WhenRequestReceived() {
            // Arrange
            Friendship friendship = createFriendship(USER_ID_2, USER_ID_1, FriendshipStatus.PENDING);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("PENDING_INCOMING");
        }

        @Test
        @DisplayName("Should return BLOCKED when blocked")
        void getFriendshipStatus_ReturnsBlocked_WhenBlocked() {
            // Arrange
            Friendship friendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(friendship));

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("Should return NONE when no relationship")
        void getFriendshipStatus_ReturnsNone_WhenNoRelationship() {
            // Arrange
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.empty());

            // Act
            FriendshipStatusDto result = friendshipService.getFriendshipStatus(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(result.getStatusCode()).isEqualTo("NONE");
            assertThat(result.getFriendship()).isNull();
        }
    }

    @Nested
    @DisplayName("blockUser tests")
    class BlockUserTests {

        @Test
        @DisplayName("Should successfully block user when no existing relationship")
        void blockUser_Success_WhenNoExistingBlock() {
            // Arrange
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.blockUser(USER_ID_1, USER_ID_2);

            // Assert
            ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
            verify(friendshipRepository).save(friendshipCaptor.capture());
            
            Friendship saved = friendshipCaptor.getValue();
            assertThat(saved.getUserIdInitiator()).isEqualTo(USER_ID_1);
            assertThat(saved.getUserIdTarget()).isEqualTo(USER_ID_2);
            assertThat(saved.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);

            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_BLOCKED");
            assertThat(event.getRecipientId()).isEqualTo(USER_ID_2);
            assertThat(event.getSenderId()).isEqualTo(USER_ID_1);
        }

        @Test
        @DisplayName("Should update existing friendship to BLOCKED")
        void blockUser_UpdatesExistingRelationship_WhenFriends() {
            // Arrange
            Friendship existingFriendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.ACCEPTED);
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.of(existingFriendship));
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.blockUser(USER_ID_1, USER_ID_2);

            // Assert
            assertThat(existingFriendship.getStatus()).isEqualTo(FriendshipStatus.BLOCKED);
            assertThat(existingFriendship.getUserIdInitiator()).isEqualTo(USER_ID_1);
            assertThat(existingFriendship.getUserIdTarget()).isEqualTo(USER_ID_2);
            verify(friendshipRepository).save(existingFriendship);
        }

        @Test
        @DisplayName("Should throw exception when trying to block self")
        void blockUser_ThrowsException_WhenSelfBlock() {
            // Act & Assert
            assertThatThrownBy(() -> friendshipService.blockUser(USER_ID_1, USER_ID_1))
                    .isInstanceOf(SelfFriendshipException.class);

            verify(friendshipRepository, never()).save(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should send Kafka event after blocking")
        void blockUser_SendsKafkaEvent_AfterBlocking() {
            // Arrange
            when(friendshipRepository.findByUserIds(USER_ID_1, USER_ID_2)).thenReturn(Optional.empty());
            when(friendshipRepository.save(any(Friendship.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            friendshipService.blockUser(USER_ID_1, USER_ID_2);

            // Assert
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
        }
    }

    @Nested
    @DisplayName("unblockUser tests")
    class UnblockUserTests {

        @Test
        @DisplayName("Should successfully unblock user")
        void unblockUser_Success_WhenBlockExists() {
            // Arrange
            Friendship blockedFriendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.of(blockedFriendship));

            // Act
            friendshipService.unblockUser(USER_ID_1, USER_ID_2);

            // Assert
            verify(friendshipRepository).delete(blockedFriendship);
            
            ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(kafkaProducerService).sendNotification(eventCaptor.capture());
            
            NotificationEvent event = eventCaptor.getValue();
            assertThat(event.getType()).isEqualTo("FRIEND_UNBLOCKED");
            assertThat(event.getRecipientId()).isEqualTo(USER_ID_2);
            assertThat(event.getSenderId()).isEqualTo(USER_ID_1);
        }

        @Test
        @DisplayName("Should throw exception when no block found")
        void unblockUser_ThrowsException_WhenNoBlockFound() {
            // Arrange
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> friendshipService.unblockUser(USER_ID_1, USER_ID_2))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("не найдена");

            verify(friendshipRepository, never()).delete(any());
            verify(kafkaProducerService, never()).sendNotification(any());
        }

        @Test
        @DisplayName("Should send Kafka event after unblocking")
        void unblockUser_SendsKafkaEvent_AfterUnblocking() {
            // Arrange
            Friendship blockedFriendship = createFriendship(USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED);
            when(friendshipRepository.findByUserIdInitiatorAndUserIdTargetAndStatus(
                    USER_ID_1, USER_ID_2, FriendshipStatus.BLOCKED))
                    .thenReturn(Optional.of(blockedFriendship));

            // Act
            friendshipService.unblockUser(USER_ID_1, USER_ID_2);

            // Assert
            verify(kafkaProducerService).sendNotification(any(NotificationEvent.class));
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

    private AccountDto createAccountDto(UUID id, String firstName, String username) {
        AccountDto dto = new AccountDto();
        dto.setId(id);
        dto.setFirstName(firstName);
        dto.setUsername(username);
        return dto;
    }
}

package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * H8b: FriendshipService.acceptFriendRequest/declineFriendRequest now throw typed
 * ResponseStatusExceptions (404/409) instead of ambiguous IllegalArgumentException /
 * IllegalStateException, and every failure path must produce no repository write/delete.
 * (getRelationshipBetweenUsers already used ResponseStatusException before this task and is
 * unchanged - covered by GlobalExceptionHandlerTest's existing integration test.)
 */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private FriendshipService friendshipService;

    private final User userA = User.builder().id(1).name("Alice").lastname("A").build();
    private final User userB = User.builder().id(2).name("Bob").lastname("B").build();

    @BeforeEach
    void setUp() {
        // Suggested-Friends dependencies (UserRepository/UserMapper/creatorUserId) are unused by
        // every test in this file - none of them touch getSuggestedFriends - so -1 (creator
        // disabled) is the only value that matters here.
        friendshipService = new FriendshipService(friendshipRepository, notificationService, userRepository, userMapper, -1);
    }

    @Test
    void acceptFriendRequest_noSuchRequest_throwsNotFound_andNeverSaves() {
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> friendshipService.acceptFriendRequest(userA, userB));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Friend request not found or already processed.", ex.getReason());
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void acceptFriendRequest_alreadyAccepted_throwsConflict_andNeverSaves() {
        Friends existing = new Friends(userB, userA, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> friendshipService.acceptFriendRequest(userA, userB));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Friend request not found or already processed.", ex.getReason());
        verify(friendshipRepository, never()).save(any());
    }

    // ---- M-DUP2: sendFriendRequest - missing coverage identified during the M-DUP2 design
    // pass. Proves the real friendship source of truth (Friends/FriendshipRepository), not
    // User.friends (removed), persists request rows correctly. ----

    @Test
    void sendFriendRequest_newRequest_persistsExactlyOnePendingRow_senderToReceiver() {
        when(friendshipRepository.existsById(any(FriendshipId.class))).thenReturn(false);

        boolean result = friendshipService.sendFriendRequest(userA, userB);

        assertTrue(result);
        ArgumentCaptor<Friends> captor = ArgumentCaptor.forClass(Friends.class);
        verify(friendshipRepository, times(1)).save(captor.capture());
        Friends saved = captor.getValue();
        assertEquals(userA, saved.getUser());
        assertEquals(userB, saved.getFriend());
        assertEquals(FriendshipStatus.PENDING, saved.getStatus());
    }

    @Test
    void sendFriendRequest_alreadyExists_returnsFalse_andNeverSaves() {
        when(friendshipRepository.existsById(any(FriendshipId.class))).thenReturn(true);

        boolean result = friendshipService.sendFriendRequest(userA, userB);

        assertFalse(result);
        verify(friendshipRepository, never()).save(any());
    }

    // COR-008: self-friend requests were previously allowed outright - no check existed.
    @Test
    void sendFriendRequest_toSelf_returnsFalse_andNeverSavesOrChecksExistence() {
        boolean result = friendshipService.sendFriendRequest(userA, userA);

        assertFalse(result);
        verify(friendshipRepository, never()).existsById(any());
        verify(friendshipRepository, never()).save(any());
    }

    // COR-008: if the other party already has a PENDING request to this user (the
    // opposite-direction-simultaneous-requests race), sendFriendRequest must resolve it as a
    // mutual accept instead of creating a second, contradictory PENDING row in the other
    // direction.
    @Test
    void sendFriendRequest_reverseDirectionAlreadyPending_resolvesAsAccept_notDuplicatePending() {
        when(friendshipRepository.existsById(new FriendshipId(userA.getId(), userB.getId()))).thenReturn(false);
        Friends reversePending = new Friends(userB, userA, FriendshipStatus.PENDING, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(reversePending));
        when(friendshipRepository.findById(new FriendshipId(userA.getId(), userB.getId()))).thenReturn(Optional.empty());

        boolean result = friendshipService.sendFriendRequest(userA, userB);

        assertTrue(result);
        // The reverse PENDING row is flipped to ACCEPTED (acceptFriendRequest's own path) and a
        // new ACCEPTED reciprocal row is created - never a second PENDING row.
        assertEquals(FriendshipStatus.ACCEPTED, reversePending.getStatus());
        ArgumentCaptor<Friends> captor = ArgumentCaptor.forClass(Friends.class);
        verify(friendshipRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(f -> f.getStatus() == FriendshipStatus.PENDING));
        verify(notificationService).createNotification(eq(userB), eq(userA), eq("FRIEND_ACCEPTED"), any(), any());
        verify(notificationService, never()).createNotification(any(), any(), eq("FRIEND_REQUEST"), any(), any());
    }

    // ---- M-DUP2: acceptFriendRequest success path - missing coverage identified during the
    // M-DUP2 design pass. Proves the pending row transitions to ACCEPTED and that a reciprocal
    // ACCEPTED row is inserted exactly when required (not duplicated when one already exists). ----

    @Test
    void acceptFriendRequest_pendingRequest_transitionsToAccepted_andInsertsReciprocalRow() {
        // userA accepts a request originally sent by userB (friend=userB, user=userA).
        Friends existingRequest = new Friends(userB, userA, FriendshipStatus.PENDING, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(existingRequest));
        when(friendshipRepository.findById(new FriendshipId(userA.getId(), userB.getId()))).thenReturn(Optional.empty());

        Friends result = friendshipService.acceptFriendRequest(userA, userB);

        assertEquals(FriendshipStatus.ACCEPTED, existingRequest.getStatus());
        assertEquals(existingRequest, result);
        ArgumentCaptor<Friends> captor = ArgumentCaptor.forClass(Friends.class);
        verify(friendshipRepository, times(2)).save(captor.capture());
        List<Friends> saved = captor.getAllValues();
        assertEquals(existingRequest, saved.get(0));
        Friends reciprocal = saved.get(1);
        assertEquals(userA, reciprocal.getUser());
        assertEquals(userB, reciprocal.getFriend());
        assertEquals(FriendshipStatus.ACCEPTED, reciprocal.getStatus());
    }

    @Test
    void acceptFriendRequest_reciprocalRowAlreadyAccepted_doesNotInsertDuplicate() {
        Friends existingRequest = new Friends(userB, userA, FriendshipStatus.PENDING, LocalDateTime.now());
        Friends existingReciprocal = new Friends(userA, userB, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(existingRequest));
        when(friendshipRepository.findById(new FriendshipId(userA.getId(), userB.getId())))
                .thenReturn(Optional.of(existingReciprocal));

        friendshipService.acceptFriendRequest(userA, userB);

        // Only the original request row is saved (flipped to ACCEPTED) - the reciprocal, already
        // ACCEPTED, is left alone rather than re-saved or duplicated.
        verify(friendshipRepository, times(1)).save(any());
    }

    // COR-008 regression: reachable via the opposite-direction-pending race in sendFriendRequest
    // (A sends to B, then B sends to A before either UI refreshes) - both rows could previously
    // exist with the reciprocal stuck at PENDING even after one side called accept. The reciprocal
    // must now be brought to ACCEPTED, not silently left at PENDING because a row "exists".
    @Test
    void acceptFriendRequest_reciprocalRowExistsButStillPending_isFlippedToAccepted_notLeftInconsistent() {
        Friends existingRequest = new Friends(userB, userA, FriendshipStatus.PENDING, LocalDateTime.now());
        Friends staleReciprocal = new Friends(userA, userB, FriendshipStatus.PENDING, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(existingRequest));
        when(friendshipRepository.findById(new FriendshipId(userA.getId(), userB.getId())))
                .thenReturn(Optional.of(staleReciprocal));

        friendshipService.acceptFriendRequest(userA, userB);

        assertEquals(FriendshipStatus.ACCEPTED, existingRequest.getStatus());
        assertEquals(FriendshipStatus.ACCEPTED, staleReciprocal.getStatus());
        verify(friendshipRepository).save(existingRequest);
        verify(friendshipRepository).save(staleReciprocal);
        // No brand-new reciprocal row is ever constructed - the existing (formerly stale) one is
        // reused and updated in place.
        verify(friendshipRepository, times(2)).save(any());
    }

    @Test
    void declineFriendRequest_noSuchRequest_throwsNotFound_andNeverDeletes() {
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> friendshipService.declineFriendRequest(userB, userA));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Friend request not found.", ex.getReason());
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void declineFriendRequest_nonPendingRequest_throwsConflict_andNeverDeletes() {
        Friends alreadyAccepted = new Friends(userA, userB, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.of(alreadyAccepted));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> friendshipService.declineFriendRequest(userB, userA));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Cannot decline a non-pending request.", ex.getReason());
        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void declineFriendRequest_pendingRequest_succeeds_andDeletes() {
        Friends pending = new Friends(userA, userB, FriendshipStatus.PENDING, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.of(pending));

        friendshipService.declineFriendRequest(userB, userA);

        verify(friendshipRepository).delete(pending);
    }

    // ---- H7 verification: getAcceptedFriends is the single status-aware source PostService's
    // getFeedPosts relies on to exclude PENDING connections from the feed. Genuinely missing
    // coverage - no existing test exercised this method at all before this task. ----

    @Test
    void getAcceptedFriends_returnsUsersFromAcceptedStatusQuery_mappedFromFriendsEntity() {
        Friends acceptedRow = new Friends(userA, userB, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findAllByUserAndStatus(userA, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(acceptedRow));

        List<User> result = friendshipService.getAcceptedFriends(userA);

        assertEquals(List.of(userB), result);
        // Proves the service queries specifically by ACCEPTED - a PENDING-status query for the
        // same user is never made, so a pending-only connection can never surface via this path.
        verify(friendshipRepository, never()).findAllByUserAndStatus(userA, FriendshipStatus.PENDING);
    }

    @Test
    void getAcceptedFriends_noAcceptedRows_returnsEmpty() {
        // Simulates a user with only a PENDING connection: the ACCEPTED-status query itself
        // (the only one getFeedPosts ever consults) legitimately returns nothing.
        when(friendshipRepository.findAllByUserAndStatus(userA, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());

        List<User> result = friendshipService.getAcceptedFriends(userA);

        assertTrue(result.isEmpty());
    }

    // ---- M-DUP2: removeFriend - missing coverage identified during the M-DUP2 design pass.
    // removeFriend itself deletes one direction's row; FriendshipController.removeFriendship
    // (already covered by FriendshipControllerTest) calls it twice, once per direction, to
    // dismantle both reciprocal rows. ----

    @Test
    void removeFriend_existingRow_deletesIt() {
        Friends row = new Friends(userA, userB, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.of(row));

        friendshipService.removeFriend(userA, userB);

        verify(friendshipRepository).delete(row);
    }

    @Test
    void removeFriend_noSuchRow_doesNothing() {
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.empty());

        friendshipService.removeFriend(userA, userB);

        verify(friendshipRepository, never()).delete(any());
    }

    @Test
    void removeFriendship_deletesBothDirectionalRows() {
        Friends forward = new Friends(userA, userB, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        Friends reverse = new Friends(userB, userA, FriendshipStatus.ACCEPTED, LocalDateTime.now());
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.of(forward));
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.of(reverse));

        friendshipService.removeFriendship(userA, userB);

        verify(friendshipRepository).delete(forward);
        verify(friendshipRepository).delete(reverse);
    }

    @Test
    void removeFriendship_noSuchRows_doesNothing() {
        when(friendshipRepository.findByUserAndFriend(userA, userB)).thenReturn(Optional.empty());
        when(friendshipRepository.findByUserAndFriend(userB, userA)).thenReturn(Optional.empty());

        friendshipService.removeFriendship(userA, userB);

        verify(friendshipRepository, never()).delete(any());
    }
}

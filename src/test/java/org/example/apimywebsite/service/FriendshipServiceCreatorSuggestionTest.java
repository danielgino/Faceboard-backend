package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.SuggestedFriendsResponseDTO;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SUG-004: reserved creator slot on the initial Suggested Friends load. The creator is
 * identified ONLY by the configured numeric id (never by name/username in any query).
 *
 * The reserved slot is integrated into the page-size calculation up front (regularLimit =
 * safeLimit - 1, with the regular query explicitly excluding the creator's id via
 * extraExcludeId) rather than fetching a full-size page and trimming a candidate afterward - the
 * point of these tests is proving that a regular candidate bumped out of the visible page by the
 * creator slot is NEVER silently skipped on the next "Show more" call. (An earlier version of
 * this file asserted the opposite invariant - that reserving the slot must leave nextCursor
 * unchanged versus a full-size query - which was actually asserting the bug: it forced
 * nextCursor to point past a candidate that had been trimmed out of the page and never shown.)
 */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceCreatorSuggestionTest {

    private static final int CREATOR_ID = 7;

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private final User me = User.builder().id(42).name("Me").lastname("M").build();
    private final User creator = User.builder().id(CREATOR_ID).name("Daniel").lastname("Gino").build();

    private FriendshipService service(int creatorUserId) {
        return new FriendshipService(friendshipRepository, notificationService, userRepository, userMapper, creatorUserId);
    }

    private static User user(int id) {
        return User.builder().id(id).name("U" + id).lastname("L").build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(userMapper.toPublicFriendDTO(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            PublicFriendDTO dto = new PublicFriendDTO();
            dto.setId(u.getId());
            return dto;
        });
        lenient().when(userRepository.findSuggestedFriendsBetween(anyInt(), anyInt(), anyInt(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private void noExistingRelationshipWithCreator() {
        when(friendshipRepository.existsById(new FriendshipId(me.getId(), CREATOR_ID))).thenReturn(false);
        when(friendshipRepository.existsById(new FriendshipId(CREATOR_ID, me.getId()))).thenReturn(false);
        when(userRepository.existsById(CREATOR_ID)).thenReturn(true);
        when(userRepository.findById(CREATOR_ID)).thenReturn(Optional.of(creator));
    }

    // The core regression test: regular candidates A,B,C,D (101..104), limit=3, creator
    // eligible. First response must be [Creator, A, B] (2 regular slots, not 3-then-trimmed).
    // The next "Show more" call, continuing from that response's own cursor/seed/wrapped, must
    // still return C - it must never have been silently consumed just because the creator took
    // one of the three display slots on page one.
    @Test
    void creatorEligible_regularSlotIsReducedUpFront_soABumpedCandidateIsNeverSkippedOnShowMore() {
        noExistingRelationshipWithCreator();

        // Fresh traversal: regularLimit = 3 - 1 = 2, so the probe asks for 2+1 = 3.
        when(userRepository.findSuggestedFriendsAfter(eq(42), anyInt(), eq(CREATOR_ID), eq(pageRequestOfSize(3))))
                .thenReturn(List.of(user(101), user(102), user(103))); // A, B, C - proves a 3rd (C) exists

        SuggestedFriendsResponseDTO first = service(CREATOR_ID).getSuggestedFriends(me, null, null, null, 3);

        assertEquals(List.of(CREATOR_ID, 101, 102), first.getUsers().stream().map(PublicFriendDTO::getId).toList());
        assertTrue(first.isHasMore());
        assertEquals(102, first.getNextCursor()); // B - the last REGULAR item actually shown, not C

        // Show More: a real cursor/seed/wrapped now, so this is no longer a fresh traversal -
        // no creator reservation, full requested limit (3) for regular candidates, no extra
        // exclusion.
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(102), eq(-1), eq(pageRequestOfSize(4))))
                .thenReturn(List.of(user(103), user(104))); // C, D - only two candidates left

        SuggestedFriendsResponseDTO second = service(CREATOR_ID)
                .getSuggestedFriends(me, first.getNextCursor(), first.getSeed(), first.isWrapped(), 3);

        List<Integer> secondIds = second.getUsers().stream().map(PublicFriendDTO::getId).toList();
        assertTrue(secondIds.contains(103), "C must not be skipped just because the creator occupied a reserved slot on page one");
        assertEquals(List.of(103, 104), secondIds);
    }

    @Test
    void creatorEligible_regularQueryExplicitlyExcludesCreatorId_soDuplicationIsStructurallyImpossible() {
        noExistingRelationshipWithCreator();
        when(userRepository.findSuggestedFriendsAfter(eq(42), anyInt(), eq(CREATOR_ID), any(Pageable.class)))
                .thenReturn(List.of(user(10), user(11)));

        SuggestedFriendsResponseDTO response = service(CREATOR_ID).getSuggestedFriends(me, null, null, null, 3);

        verify(userRepository).findSuggestedFriendsAfter(eq(42), anyInt(), eq(CREATOR_ID), any(Pageable.class));
        List<Integer> ids = response.getUsers().stream().map(PublicFriendDTO::getId).toList();
        assertEquals(1, ids.stream().filter(id -> id == CREATOR_ID).count());
        assertEquals(List.of(CREATOR_ID, 10, 11), ids);
    }

    @Test
    void eligibleCreator_fewerThanTwoRegularCandidatesExist_stillReturnsWhatsAvailable() {
        noExistingRelationshipWithCreator();
        when(userRepository.findSuggestedFriendsAfter(eq(42), anyInt(), eq(CREATOR_ID), any(Pageable.class)))
                .thenReturn(List.of(user(10))); // only 1 regular candidate exists at all

        SuggestedFriendsResponseDTO response = service(CREATOR_ID).getSuggestedFriends(me, null, null, null, 3);

        assertEquals(List.of(CREATOR_ID, 10), response.getUsers().stream().map(PublicFriendDTO::getId).toList());
        assertFalse(response.isHasMore());
    }

    @Test
    void alreadyFriendsWithCreator_notInjected_fillsAllThreeSlotsNormally() {
        when(friendshipRepository.existsById(new FriendshipId(me.getId(), CREATOR_ID))).thenReturn(true);
        when(userRepository.findSuggestedFriendsAfter(eq(42), anyInt(), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(10), user(11), user(12)));

        SuggestedFriendsResponseDTO response = service(CREATOR_ID).getSuggestedFriends(me, null, null, null, 3);

        assertEquals(List.of(10, 11, 12), response.getUsers().stream().map(PublicFriendDTO::getId).toList());
        verify(userRepository, never()).findById(CREATOR_ID);
    }

    @Test
    void callerIsCreator_notInjected() {
        User creatorAsCaller = User.builder().id(CREATOR_ID).name("Daniel").lastname("Gino").build();
        when(userRepository.findSuggestedFriendsAfter(eq(CREATOR_ID), anyInt(), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(10)));

        service(CREATOR_ID).getSuggestedFriends(creatorAsCaller, null, null, null, 3);

        verifyNoInteractions(friendshipRepository);
        verify(userRepository, never()).findById(CREATOR_ID);
    }

    @Test
    void creatorNotConfigured_noInjectionAttempted() {
        when(userRepository.findSuggestedFriendsAfter(eq(42), anyInt(), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(10)));

        service(-1).getSuggestedFriends(me, null, null, null, 3);

        verifyNoInteractions(friendshipRepository);
    }

    @Test
    void showMoreCall_neverReinjectsCreator_evenIfOtherwiseEligible() {
        // A real cursor/seed/wrapped (i.e. not a fresh traversal) must skip creator injection
        // entirely - isFreshTraversal short-circuits before isCreatorEligible is even called, so
        // no creator-eligibility stubbing is needed here to prove it, and the full requested
        // limit (3) is used with no extra exclusion.
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(100), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(101), user(102)));

        SuggestedFriendsResponseDTO response = service(CREATOR_ID).getSuggestedFriends(me, 100, 50, false, 3);

        assertFalse(response.getUsers().stream().anyMatch(u -> u.getId() == CREATOR_ID));
        verify(userRepository, never()).findById(CREATOR_ID);
        verifyNoInteractions(friendshipRepository);
    }

    private static Pageable pageRequestOfSize(int size) {
        return org.springframework.data.domain.PageRequest.of(0, size);
    }
}

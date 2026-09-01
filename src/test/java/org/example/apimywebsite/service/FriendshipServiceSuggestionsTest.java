package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.SuggestedFriendsResponseDTO;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SUG-002: GET /friendship/suggestions cursor/seed/wrapped protocol. The core correctness
 * property under test is that phase B (post-wrap) is always bounded strictly below `seed`, so a
 * traversal can never re-return an id phase A already handed out - and that hasMore always comes
 * from an actual probe (limit+1 fetch), never from "the page happened to be full".
 *
 * creatorUserId is -1 (disabled) throughout this file, so extraExcludeId is always -1 too - the
 * reserved-creator-slot feature (and its own effect on page-size/extraExcludeId) has its own
 * dedicated test file, FriendshipServiceCreatorSuggestionTest.
 */
@ExtendWith(MockitoExtension.class)
class FriendshipServiceSuggestionsTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;

    private FriendshipService friendshipService;

    private final User me = User.builder().id(42).name("Me").lastname("M").build();

    @BeforeEach
    void setUp() {
        friendshipService = new FriendshipService(friendshipRepository, notificationService, userRepository, userMapper, -1);
        lenient().when(userMapper.toPublicFriendDTO(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            PublicFriendDTO dto = new PublicFriendDTO();
            dto.setId(u.getId());
            return dto;
        });
        // Nothing in phase B unless a test explicitly stubs it - most phase-A-only tests never
        // reach phase B at all, but a couple do reach the "peek" call.
        lenient().when(userRepository.findSuggestedFriendsBetween(anyInt(), anyInt(), anyInt(), eq(-1), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private static User user(int id) {
        return User.builder().id(id).name("U" + id).lastname("L").build();
    }

    // SUG-003: matches FriendshipService.computeDailySeed's exact formula, so tests can assert
    // the real expected value instead of a fixed literal (the seed is deterministic for a given
    // (meId, day) pair, but is no longer simply meId itself).
    private static int expectedDailySeed(int meId) {
        long dayEpoch = java.time.LocalDate.now().toEpochDay();
        int mixed = java.util.Objects.hash(meId, dayEpoch);
        return meId + Math.floorMod(mixed, 500);
    }

    @Test
    void firstRequest_noCursorOrSeed_seedsFromDailyDeterministicValue() {
        int seed = expectedDailySeed(42);
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(seed), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(50), user(51), user(52), user(53)));

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, null, null, null, 3);

        verify(userRepository).findSuggestedFriendsAfter(eq(42), eq(seed), eq(-1), any(Pageable.class));
        assertEquals(seed, response.getSeed());
        assertFalse(response.isWrapped());
    }

    @Test
    void dailySeed_differsBetweenUsers_forTheSameDay() {
        User otherUser = User.builder().id(43).name("Other").lastname("O").build();
        when(userRepository.findSuggestedFriendsAfter(anyInt(), anyInt(), eq(-1), any(Pageable.class))).thenReturn(List.of());

        SuggestedFriendsResponseDTO responseA = friendshipService.getSuggestedFriends(me, null, null, null, 3);
        SuggestedFriendsResponseDTO responseB = friendshipService.getSuggestedFriends(otherUser, null, null, null, 3);

        assertEquals(expectedDailySeed(42), responseA.getSeed());
        assertEquals(expectedDailySeed(43), responseB.getSeed());
    }

    @Test
    void fullPhaseAPage_probeConfirmsMore_hasMoreTrue_neverTouchesPhaseB() {
        // limit=3 -> probe asks for 4; returning 4 proves a genuine 4th candidate exists.
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(100), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(101), user(102), user(103), user(104)));

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, 100, 42, false, 3);

        assertEquals(List.of(101, 102, 103), response.getUsers().stream().map(PublicFriendDTO::getId).toList());
        assertTrue(response.isHasMore());
        assertEquals(103, response.getNextCursor());
        assertFalse(response.isWrapped());
        verify(userRepository, never()).findSuggestedFriendsBetween(anyInt(), anyInt(), anyInt(), anyInt(), any(Pageable.class));
    }

    @Test
    void exactFitPhaseAPage_probeFindsNoExtra_hasMoreFalse_becauseNoWaitingPhaseB() {
        // Asked for limit+1=4, got exactly 3 back -> phase A is genuinely exhausted right there.
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(100), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(101), user(102), user(103)));
        // Phase-B peek (the @BeforeEach default) returns empty -> nothing left anywhere.

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, 100, 42, false, 3);

        assertEquals(3, response.getUsers().size());
        assertFalse(response.isHasMore());
    }

    @Test
    void phaseAExhaustedMidPage_wrapsIntoPhaseB_boundedBySeed_notPastIt() {
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(500), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(600))); // only 1 of 3 needed; phase A truly out
        when(userRepository.findSuggestedFriendsBetween(eq(42), eq(-1), eq(500), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(3), user(7))); // exactly the 2 remaining, no extra probe row

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, 500, 500, false, 3);

        // Phase B call is bounded above by the seed (500), never by anything from phase A (600).
        ArgumentCaptor<Integer> highBound = ArgumentCaptor.forClass(Integer.class);
        verify(userRepository).findSuggestedFriendsBetween(eq(42), eq(-1), highBound.capture(), eq(-1), any(Pageable.class));
        assertEquals(500, highBound.getValue());

        assertEquals(List.of(600, 3, 7), response.getUsers().stream().map(PublicFriendDTO::getId).toList());
        assertTrue(response.isWrapped());
        assertEquals(500, response.getSeed());
        assertEquals(7, response.getNextCursor());
        assertFalse(response.isHasMore()); // wrapProbe returned exactly `remaining`, no extra row
    }

    @Test
    void alreadyWrapped_continuationStaysBoundedBySeed_andCanNeverReenterPhaseA() {
        // Client is already in phase B (wrapped=true), continuing from cursor=7 toward seed=500.
        when(userRepository.findSuggestedFriendsBetween(eq(42), eq(7), eq(500), eq(-1), any(Pageable.class)))
                .thenReturn(List.of(user(8), user(9)));

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, 7, 500, true, 3);

        verify(userRepository, never()).findSuggestedFriendsAfter(anyInt(), anyInt(), anyInt(), any(Pageable.class));
        assertEquals(List.of(8, 9), response.getUsers().stream().map(PublicFriendDTO::getId).toList());
        assertTrue(response.isWrapped());
        assertFalse(response.isHasMore());
    }

    @Test
    void wholeTraversalExhausted_bothPhasesEmpty_hasMoreFalse_emptyPage() {
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(expectedDailySeed(42)), eq(-1), any(Pageable.class)))
                .thenReturn(List.of());

        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(me, null, null, null, null);

        assertTrue(response.getUsers().isEmpty());
        assertFalse(response.isHasMore());
    }

    @Test
    void limitAboveMax_isClampedToTen() {
        when(userRepository.findSuggestedFriendsAfter(eq(42), eq(expectedDailySeed(42)), eq(-1), any(Pageable.class)))
                .thenReturn(List.of());

        friendshipService.getSuggestedFriends(me, null, null, null, 999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findSuggestedFriendsAfter(eq(42), eq(expectedDailySeed(42)), eq(-1), pageableCaptor.capture());
        assertEquals(11, pageableCaptor.getValue().getPageSize()); // clamped limit (10) + 1 probe row
    }
}

package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendRequestDTO;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.SuggestedFriendsResponseDTO;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class FriendshipService {

    // SUG-001: default/max page size for GET /friendship/suggestions - same "small constant on
    // the service" convention as UserService.MAX_SEARCH_RESULTS. Never let the client request an
    // arbitrarily large page.
    private static final int DEFAULT_SUGGESTIONS_LIMIT = 3;
    private static final int MAX_SUGGESTIONS_LIMIT = 10;

    // SUG-003: how far the daily seed can drift from the caller's own id, in either direction.
    // Deliberately small - the seed should still land close to real id territory (see
    // computeDailySeed), not require an extra MAX(id) query to stay bounded.
    private static final int DAILY_SEED_SPREAD = 500;

    private final FriendshipRepository friendshipRepository;

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // SUG-003: the Faceboard creator account, reserved for one Suggested Friends slot when
    // eligible. Identified ONLY by this configured numeric id - never by name/username anywhere
    // in this class or its queries. -1 (the default) means "not configured", which simply
    // disables the reservation entirely (isCreatorEligible short-circuits on it).
    private final int creatorUserId;

    public FriendshipService(FriendshipRepository friendshipRepository, @Lazy NotificationService notificationService,
                              UserRepository userRepository, UserMapper userMapper,
                              @Value("${faceboard.creator-user-id:-1}") int creatorUserId) {
        this.friendshipRepository = friendshipRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.creatorUserId = creatorUserId;
    }

    // COR-008 fix: two defects lived here. (1) A user could send a request to themself - there
    // was no check at all. (2) Only the forward direction (user->friend) was checked for an
    // existing row, so if friend had already sent user a PENDING request, this created a second,
    // opposite-direction PENDING row instead of recognizing that both parties already want to be
    // friends. That left the two sides permanently disagreeing about the relationship (one row
    // PENDING, the other never created) until someone happened to call accept on the right row.
    // Detecting the reverse-pending case and resolving it via the existing acceptFriendRequest
    // path (instead of inventing a new state transition) turns "both sides sent a request" into
    // an immediate mutual friendship, which is what the users actually intended.
    @Transactional
    public boolean sendFriendRequest(User user, User friend) {
        if (user.getId() == friend.getId()) {
            return false;
        }
        FriendshipId friendshipId = new FriendshipId(user.getId(), friend.getId());
        if (friendshipRepository.existsById(friendshipId)) {
            return false;
        }

        Friends reverse = friendshipRepository.findByUserAndFriend(friend, user).orElse(null);
        if (reverse != null && reverse.getStatus() == FriendshipStatus.PENDING) {
            acceptFriendRequest(user, friend);
            return true;
        }

        String content = user.getFullName() + " Send you a Friend Request";
          notificationService.createNotification(friend, user, "FRIEND_REQUEST", content, null);
        Friends friends = new Friends(user, friend, FriendshipStatus.PENDING, LocalDateTime.now());
        friends.setId(friendshipId);


        friendshipRepository.save(friends);
        return true;
    }


    @Transactional
    public Friends acceptFriendRequest(User user, User friend) {
        Friends existingRequest = friendshipRepository.findByUserAndFriend(friend, user)
                .orElse(null);

        if (existingRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found or already processed.");
        }
        if (!existingRequest.getStatus().equals(FriendshipStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request not found or already processed.");
        }
        existingRequest.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(existingRequest);


        // COR-008 fix: previously only checked existsById, so a reciprocal row that already
        // existed but was still PENDING (reachable via the opposite-direction-pending race above)
        // was left untouched - one direction ACCEPTED, the other stuck PENDING forever. Now the
        // reciprocal row is read by its real id and explicitly brought to ACCEPTED whenever it
        // isn't already, not just created when entirely absent.
        FriendshipId reciprocalId = new FriendshipId(user.getId(), friend.getId());
        Friends reciprocal = friendshipRepository.findById(reciprocalId).orElse(null);
        if (reciprocal == null) {
            Friends reciprocalFriendship = new Friends(user, friend, FriendshipStatus.ACCEPTED, LocalDateTime.now());
            friendshipRepository.save(reciprocalFriendship);
        } else if (reciprocal.getStatus() != FriendshipStatus.ACCEPTED) {
            reciprocal.setStatus(FriendshipStatus.ACCEPTED);
            friendshipRepository.save(reciprocal);
        }
        String content = user.getFullName() + " Accept You Friend Request";
        notificationService.createNotification(friend, user, "FRIEND_ACCEPTED", content, null);

        return existingRequest;
    }


    @Transactional
    public void declineFriendRequest(User receiver, User sender) {
        Friends request = friendshipRepository.findByUserAndFriend(sender, receiver)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Friend request not found."));

        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot decline a non-pending request.");
        }

        friendshipRepository.delete(request);

    }
    public FriendRequestDTO getRelationshipBetweenUsers(User user, User friend) {
        Friends outgoingRequest = friendshipRepository.findByUserAndFriend(user, friend).orElse(null);
        if (outgoingRequest != null) {
            return new FriendRequestDTO(user.getId(), friend.getId(), outgoingRequest.getStatus().name());
        }
        Friends incomingRequest = friendshipRepository.findByUserAndFriend(friend, user).orElse(null);

        if (incomingRequest != null) {
            return new FriendRequestDTO(friend.getId(), user.getId(), incomingRequest.getStatus().name());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No friendship found between users");
    }

    public List<User> getAcceptedFriends(User user) {
        return friendshipRepository.findAllByUserAndStatus(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friends::getFriend)
                .toList();
    }

    public List<User> getAcceptedFriendsPage(User user, String query, Pageable pageable) {
        return friendshipRepository.searchAcceptedFriendsPage(user, FriendshipStatus.ACCEPTED, query, pageable)
                .stream()
                .map(Friends::getFriend)
                .toList();
    }

    public void removeFriend(User user, User friend) {
        Optional<Friends> friendship = friendshipRepository.findByUserAndFriend(user, friend);
        friendship.ifPresent(friendshipRepository::delete);
    }

    @Transactional
    public void removeFriendship(User user, User friend) {
        removeFriend(user, friend);
        removeFriend(friend, user);
    }

    // SUG-002: GET /friendship/suggestions. Keyset pagination over users.id (no OFFSET, no
    // ORDER BY RAND(), no full-table load, no suggestion-history table) - see
    // UserRepository.findSuggestedFriendsAfter/Between for the actual exclusion queries.
    //
    // One traversal has a stable seed/boundary so it can never revisit an id it has already
    // returned, across any number of "Show more" calls:
    //
    //   - `seed` is fixed for the whole traversal: the very first call's effective cursor (the
    //     caller's own id, if the first call passes no cursor at all - see below). The client
    //     must echo it back unchanged on every subsequent call of the same traversal.
    //   - Phase A ("wrapped" = false): id > cursor, unbounded above. Ordinary forward paging.
    //   - Phase B ("wrapped" = true): seed's-lower-boundary < id < seed. Once phase A runs off
    //     the end of the users table, the traversal moves into phase B to cover the ids below
    //     the seed - but phase B is hard-bounded above by `seed`, so it can never climb back up
    //     into the id > seed range phase A already returned. Once phase B itself is exhausted,
    //     the whole traversal is genuinely done (hasMore = false).
    //
    // Starting position (very first request, no cursor/seed given): seeded from the caller's own
    // id rather than always starting at 0 - costs nothing extra (no additional query, no sort)
    // and spreads different users' starting point across the id range, since every user has a
    // distinct id.
    //
    // hasMore is always the result of an actual probe (fetching one extra candidate beyond what
    // the page needs), never inferred from "this page happened to be full".
    //
    // The frontend still keeps a small "already shown this session" set as a defensive backstop
    // (e.g. a user created mid-traversal could in principle land in an unvisited gap), but with
    // seed/wrapped round-tripped like this it is no longer load-bearing for correctness - a
    // single traversal cannot revisit an id on its own.
    //
    // SUG-004: on the very first call of a fresh traversal only (no cursor/seed/wrapped from the
    // client), one slot is reserved for the creator account when eligible. The reserved slot is
    // integrated into the page-size calculation UP FRONT - the regular keyset query is asked for
    // exactly (safeLimit - 1) candidates, not the full safeLimit, so nextCursor/hasMore always
    // reflect the regular candidates that were actually returned, never one that got trimmed
    // away afterward to make room for the creator. (An earlier version computed a full
    // safeLimit-sized regular page first and trimmed its last entry post-hoc when injecting the
    // creator - nextCursor still pointed past that trimmed entry, silently and permanently
    // skipping it on the next "Show more" call. That is the bug this shape fixes.)
    //
    // The regular query also explicitly excludes the creator's id whenever a slot is reserved
    // for them (extraExcludeId), so they can never be double-counted by also appearing as an
    // ordinary candidate in the same page - no post-hoc dedup/removal needed.
    public SuggestedFriendsResponseDTO getSuggestedFriends(User me, Integer cursor, Integer seedParam, Boolean wrappedParam, Integer limit) {
        boolean isFreshTraversal = cursor == null && seedParam == null && wrappedParam == null;
        int safeLimit = (limit == null || limit <= 0) ? DEFAULT_SUGGESTIONS_LIMIT : Math.min(limit, MAX_SUGGESTIONS_LIMIT);

        boolean reserveCreatorSlot = isFreshTraversal && isCreatorEligible(me);
        int regularLimit = reserveCreatorSlot ? safeLimit - 1 : safeLimit;
        int extraExcludeId = reserveCreatorSlot ? creatorUserId : -1;

        SuggestedFriendsResponseDTO response = computeKeysetPage(me, cursor, seedParam, wrappedParam, regularLimit, extraExcludeId);

        if (reserveCreatorSlot) {
            prependCreator(response);
        }
        return response;
    }

    private SuggestedFriendsResponseDTO computeKeysetPage(User me, Integer cursor, Integer seedParam, Boolean wrappedParam,
                                                            int regularLimit, int extraExcludeId) {
        // SUG-003: seed is now a daily-deterministic value (see computeDailySeed) instead of
        // always just the caller's own id - stable for one user across one calendar day, but
        // different the next day and different between users.
        int seed = (seedParam == null) ? computeDailySeed(me.getId()) : seedParam;
        int startCursor = (cursor == null) ? seed : cursor;
        boolean wrapped = Boolean.TRUE.equals(wrappedParam);

        List<User> page;
        Integer nextCursor;
        boolean hasMore;

        if (!wrapped) {
            List<User> probe = userRepository.findSuggestedFriendsAfter(me.getId(), startCursor, extraExcludeId, PageRequest.of(0, regularLimit + 1));
            boolean moreInPhaseA = probe.size() > regularLimit;
            page = new ArrayList<>(probe.subList(0, Math.min(regularLimit, probe.size())));

            if (moreInPhaseA) {
                // Page fully satisfied from phase A alone; still more ahead in phase A.
                nextCursor = page.get(page.size() - 1).getId();
                return buildResponse(page, seed, false, nextCursor, true);
            }

            // Phase A is exhausted (0..regularLimit items collected). Try phase B for the rest -
            // bounded strictly below `seed`, so it can never re-enter phase A's territory.
            wrapped = true;
            int remaining = regularLimit - page.size();
            List<User> wrapProbe = userRepository.findSuggestedFriendsBetween(me.getId(), -1, seed, extraExcludeId, PageRequest.of(0, remaining + 1));
            boolean moreInPhaseB = wrapProbe.size() > remaining;
            List<User> wrapTaken = wrapProbe.subList(0, Math.min(remaining, wrapProbe.size()));
            page.addAll(wrapTaken);

            // If nothing was actually consumed from phase B yet (page was already an exact fit
            // from phase A alone), the next call must still resume phase B from its own start
            // (-1), not from phase A's leftover cursor - phase B's valid range is entirely
            // different from phase A's. (Written as explicit if/else, not a nested ternary: a
            // ternary mixing an `int` branch with a `null` Integer branch gets unboxed by the
            // compiler and throws NPE at runtime whenever the null branch is the one taken.)
            if (!wrapTaken.isEmpty()) {
                nextCursor = wrapTaken.get(wrapTaken.size() - 1).getId();
            } else if (moreInPhaseB) {
                nextCursor = -1;
            } else if (!page.isEmpty()) {
                nextCursor = page.get(page.size() - 1).getId();
            } else {
                nextCursor = null;
            }
            hasMore = moreInPhaseB;
            return buildResponse(page, seed, wrapped, nextCursor, hasMore);
        }

        // Already in phase B: bounded scan strictly below `seed`.
        List<User> wrapProbe = userRepository.findSuggestedFriendsBetween(me.getId(), startCursor, seed, extraExcludeId, PageRequest.of(0, regularLimit + 1));
        hasMore = wrapProbe.size() > regularLimit;
        page = new ArrayList<>(wrapProbe.subList(0, Math.min(regularLimit, wrapProbe.size())));
        nextCursor = page.isEmpty() ? startCursor : page.get(page.size() - 1).getId();
        return buildResponse(page, seed, true, nextCursor, hasMore);
    }

    private SuggestedFriendsResponseDTO buildResponse(List<User> page, int seed, boolean wrapped, Integer nextCursor, boolean hasMore) {
        List<PublicFriendDTO> users = page.stream().map(userMapper::toPublicFriendDTO).toList();
        return SuggestedFriendsResponseDTO.builder()
                .users(users)
                .nextCursor(nextCursor)
                .seed(seed)
                .wrapped(wrapped)
                .hasMore(hasMore)
                .build();
    }

    // SUG-003: daily-deterministic seed. Objects.hash(meId, dayEpoch) mixes the two into one int
    // deterministically (same inputs -> same output, always); Math.floorMod folds that into a
    // small +/-DAILY_SEED_SPREAD-ish offset added to the caller's own id, so the seed still lands
    // close to real id territory (no MAX(id) query needed to keep it sane) while being stable for
    // one user for one calendar day, different the next day, and different between users.
    private int computeDailySeed(int meId) {
        long dayEpoch = LocalDate.now().toEpochDay();
        int mixed = Objects.hash(meId, dayEpoch);
        int offset = Math.floorMod(mixed, DAILY_SEED_SPREAD);
        return meId + offset;
    }

    // SUG-003: identified ONLY by the configured id - never by name/username, and never via a
    // query that filters on one. Ineligible whenever: not configured, the caller IS the creator,
    // a friends row already exists in either direction (covers pending outgoing/incoming and
    // accepted alike, same as the regular suggestion exclusion), or the account doesn't exist.
    private boolean isCreatorEligible(User me) {
        if (creatorUserId <= 0 || creatorUserId == me.getId()) {
            return false;
        }
        boolean relationshipExists = friendshipRepository.existsById(new FriendshipId(me.getId(), creatorUserId))
                || friendshipRepository.existsById(new FriendshipId(creatorUserId, me.getId()));
        if (relationshipExists) {
            return false;
        }
        return userRepository.existsById(creatorUserId);
    }

    // SUG-004: purely a post-processing step on an already-fully-computed response - never
    // touches nextCursor/seed/wrapped/hasMore. The regular page was already sized down to
    // (safeLimit - 1) and queried with the creator explicitly excluded (see
    // getSuggestedFriends/UserRepository.findSuggestedFriendsAfter's extraExcludeId), so there is
    // never anything to remove or trim here - just prepend.
    private void prependCreator(SuggestedFriendsResponseDTO response) {
        User creator = userRepository.findById(creatorUserId).orElse(null);
        if (creator == null) {
            return;
        }

        List<PublicFriendDTO> users = new ArrayList<>(response.getUsers());
        users.add(0, userMapper.toPublicFriendDTO(creator));
        response.setUsers(users);
    }
}

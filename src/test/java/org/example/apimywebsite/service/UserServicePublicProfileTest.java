package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.PublicUserProfileDTO;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.dto.UserSearchResultDTO;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Public-profile finding: proves the fix is structural, not just a serialization-level
 * field hide. getPublicUserProfileById (GET /user/by-id) must never touch MessageRepository at
 * all, unlike the untouched getUserDTOById (GET /auth/me), which still must build last-message
 * previews for the caller's own account view.
 */
@ExtendWith(MockitoExtension.class)
class UserServicePublicProfileTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private FriendshipService friendshipService;
    @Mock
    private AuthHelper authHelper;

    private UserService userService;

    private static final int TARGET_ID = 5;

    @BeforeEach
    void setUp() {
        // UserService's non-constructor dependencies (jwtUtil, userMapper, messageRepository,
        // friendshipService, passwordEncoder) are field-injected (@Autowired) in production,
        // not constructor params - @InjectMocks only performs one injection strategy (here,
        // constructor), so the rest must be set explicitly, matching JwtAuthFilterTest's
        // established pattern for the same situation.
        userService = new UserService(userRepository, cloudinaryService);
        ReflectionTestUtils.setField(userService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userService, "messageRepository", messageRepository);
        ReflectionTestUtils.setField(userService, "friendshipService", friendshipService);
        ReflectionTestUtils.setField(userService, "authHelper", authHelper);
    }

    // Demo Mode: DemoScope.assertAccessible/the demo-search branch both call
    // authHelper.getCurrentUser() - a plain non-demo caller so every existing test here keeps
    // exercising the real (non-demo) code path unchanged.
    private void loginAsRegularUser() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("caller").build());
    }

    @Test
    void getPublicUserProfileById_neverInteractsWithMessageRepository() {
        loginAsRegularUser();
        User target = User.builder().id(TARGET_ID).userName("bob").build();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(friendshipService.getAcceptedFriends(target)).thenReturn(List.of());
        when(userMapper.toPublicUserProfileDTO(eq(target), anyList()))
                .thenReturn(new PublicUserProfileDTO());

        userService.getPublicUserProfileById(TARGET_ID);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void getPublicUserProfileById_buildsFriendsViaPublicFriendMapping_notMessageEnrichedMapping() {
        loginAsRegularUser();
        User target = User.builder().id(TARGET_ID).userName("bob").build();
        User friend = User.builder().id(9).userName("carol").build();
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));
        when(friendshipService.getAcceptedFriends(target)).thenReturn(List.of(friend));
        PublicFriendDTO friendDto = new PublicFriendDTO();
        when(userMapper.toPublicFriendDTO(friend)).thenReturn(friendDto);
        when(userMapper.toPublicUserProfileDTO(eq(target), eq(List.of(friendDto))))
                .thenReturn(new PublicUserProfileDTO());

        userService.getPublicUserProfileById(TARGET_ID);

        verify(userMapper).toPublicFriendDTO(friend);
        verify(userMapper, never()).toFriendDTOWithMessage(any(), any(), anyInt());
    }

    @Test
    void searchUsersByName_returnsSearchResultDTOs_notFullUserDTO() {
        loginAsRegularUser();
        User match = User.builder().id(TARGET_ID).userName("bob").build();
        when(userRepository.searchByFullName(eq("bob"), any(Pageable.class))).thenReturn(List.of(match));
        UserSearchResultDTO resultDto = new UserSearchResultDTO();
        when(userMapper.toUserSearchResultDTO(match)).thenReturn(resultDto);

        List<UserSearchResultDTO> results = userService.searchUsersByName("bob");

        // Flat list, not a Page<>/wrapper - the API contract is unchanged.
        assertEquals(List.of(resultDto), results);
        verify(userMapper, never()).toUserDTO(any());
        verifyNoInteractions(messageRepository);
    }

    // ---- L-DB4A: bounded search ----

    @Test
    void searchUsersByName_passesBoundedPageable_cappedAtTwenty() {
        loginAsRegularUser();
        when(userRepository.searchByFullName(eq("bob"), any(Pageable.class))).thenReturn(List.of());

        userService.searchUsersByName("bob");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).searchByFullName(eq("bob"), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void searchUsersByName_blankQuery_returnsEmpty_neverQueriesRepository() {
        List<UserSearchResultDTO> blank = userService.searchUsersByName("   ");
        List<UserSearchResultDTO> empty = userService.searchUsersByName("");
        List<UserSearchResultDTO> nullQuery = userService.searchUsersByName(null);

        assertTrue(blank.isEmpty());
        assertTrue(empty.isEmpty());
        assertTrue(nullQuery.isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getUserDTOById_withFriends_stillQueriesMessageRepository_forOwnAccountView() {
        // Regression guard: /auth/me's underlying method must keep building last-message
        // previews for the caller's own friends - M-DB3 must not have removed this for a user
        // who actually has friends (only the empty-friend-list fast path, covered separately
        // below in UserServiceLastMessageTest, was intentionally added by M-DB3).
        User self = User.builder().id(7).userName("alice").build();
        User friend = User.builder().id(8).userName("bob").build();
        when(userRepository.findById(7)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of(friend));
        when(messageRepository.findLastMessagesBetweenUserAndFriends(eq(7), anyList()))
                .thenReturn(List.of());
        when(userMapper.toUserDTOWithFriendsAndLastMessage(eq(self), anyList()))
                .thenReturn(UserDTO.builder().id(7).build());

        userService.getUserDTOById(7);

        verify(messageRepository).findLastMessagesBetweenUserAndFriends(eq(7), anyList());
    }
}

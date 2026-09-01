package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendDTO;
import org.example.apimywebsite.dto.UpdateUserDTO;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.mapper.UserMapper;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M-DB3: UserService.getUserDTOById's last-message-per-friend enrichment (used by GET /auth/me
 * and GET /user/id, both self-only) and the removal of that same enrichment from
 * updateUserDetails (PUT /user/settings), whose known frontend callers discard the response body.
 * These are mock-level, repository-consumer-side proofs; MessageRepository.
 * findLastMessagesBetweenUserAndFriends's own JPQL correctness (the fixed operator-precedence
 * bug, NOT EXISTS anti-join, and the sentTime/id tie-break) is proven structurally in the query's
 * own code comment and the audit doc, not by a live JPA execution - no H2/Testcontainers
 * dependency exists in this project, and this task deliberately did not add one.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceLastMessageTest {

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
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, cloudinaryService);
        ReflectionTestUtils.setField(userService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userService, "messageRepository", messageRepository);
        ReflectionTestUtils.setField(userService, "friendshipService", friendshipService);
        ReflectionTestUtils.setField(userService, "passwordEncoder", passwordEncoder);
    }

    // ---- empty friend list: fast path, no pointless query ----

    @Test
    void getUserDTOById_noFriends_neverInteractsWithMessageRepository() {
        User self = User.builder().id(7).userName("alice").build();
        when(userRepository.findById(7)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of());
        when(userMapper.toUserDTOWithFriendsAndLastMessage(eq(self), eq(List.of())))
                .thenReturn(UserDTO.builder().id(7).build());

        userService.getUserDTOById(7);

        verifyNoInteractions(messageRepository);
    }

    // ---- multiple friends: each gets exactly its own matched last message ----

    @Test
    void getUserDTOById_multipleFriends_eachFriendMappedToItsOwnLastMessage_notSwapped() {
        User self = User.builder().id(1).userName("alice").build();
        User friendA = User.builder().id(2).userName("bob").build();
        User friendB = User.builder().id(3).userName("carol").build();
        when(userRepository.findById(1)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of(friendA, friendB));

        Message msgToA = new Message();
        msgToA.setId(100);
        msgToA.setSender(self);
        msgToA.setReceiver(friendA);
        msgToA.setMessage("hi bob");
        msgToA.setSentTime(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC));

        Message msgFromB = new Message();
        msgFromB.setId(101);
        msgFromB.setSender(friendB);
        msgFromB.setReceiver(self);
        msgFromB.setMessage("hi alice");
        msgFromB.setSentTime(OffsetDateTime.of(2026, 1, 1, 11, 0, 0, 0, ZoneOffset.UTC));

        when(messageRepository.findLastMessagesBetweenUserAndFriends(eq(1), anyList()))
                .thenReturn(List.of(msgToA, msgFromB));
        when(userMapper.toUserDTOWithFriendsAndLastMessage(eq(self), anyList()))
                .thenReturn(UserDTO.builder().id(1).build());

        userService.getUserDTOById(1);

        verify(userMapper).toFriendDTOWithMessage(friendA, msgToA, 1);
        verify(userMapper).toFriendDTOWithMessage(friendB, msgFromB, 1);
    }

    // ---- /auth/me and /user/id path (getUserDTOById) keeps the enriched mapper ----

    @Test
    void getUserDTOById_usesEnrichedMapper_notTheLightweightSettingsMapper() {
        User self = User.builder().id(9).userName("dave").build();
        when(userRepository.findById(9)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of());
        when(userMapper.toUserDTOWithFriendsAndLastMessage(eq(self), eq(List.of())))
                .thenReturn(UserDTO.builder().id(9).build());

        userService.getUserDTOById(9);

        verify(userMapper).toUserDTOWithFriendsAndLastMessage(eq(self), eq(List.of()));
        verify(userMapper, never()).toUserDTOWithFriends(any(), any());
    }

    // ---- /user/settings (updateUserDetails): wasted enrichment removed ----

    @Test
    void updateUserDetails_neverInteractsWithMessageRepository() {
        User self = User.builder().id(4).userName("erin").build();
        when(userRepository.findById(4)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of());
        when(userMapper.toUserDTOWithFriends(eq(self), eq(List.of())))
                .thenReturn(UserDTO.builder().id(4).build());
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setBio("new bio");

        userService.updateUserDetails(4, dto);

        verifyNoInteractions(messageRepository);
    }

    @Test
    void updateUserDetails_usesLightweightMapper_notTheEnrichedAuthMeMapper() {
        User self = User.builder().id(4).userName("erin").build();
        User friend = User.builder().id(5).userName("frank").build();
        when(userRepository.findById(4)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of(friend));
        when(userMapper.toUserDTOWithFriends(eq(self), eq(List.of(friend))))
                .thenReturn(UserDTO.builder().id(4).build());
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setBio("new bio");

        UserDTO result = userService.updateUserDetails(4, dto);

        assertEquals(4, result.getId());
        verify(userMapper).toUserDTOWithFriends(self, List.of(friend));
        verify(userMapper, never()).toUserDTOWithFriendsAndLastMessage(any(), any());
        verify(userMapper, never()).toFriendDTOWithMessage(any(), any(), anyInt());
    }

    @Test
    void updateUserDetails_stillSavesUpdatedFields_beforeBuildingLightweightResponse() {
        User self = User.builder().id(4).userName("erin").bio("old bio").build();
        when(userRepository.findById(4)).thenReturn(Optional.of(self));
        when(friendshipService.getAcceptedFriends(self)).thenReturn(List.of());
        when(userMapper.toUserDTOWithFriends(eq(self), eq(List.of())))
                .thenReturn(UserDTO.builder().id(4).build());
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setBio("new bio");

        userService.updateUserDetails(4, dto);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        assertEquals("new bio", savedCaptor.getValue().getBio());
    }
}

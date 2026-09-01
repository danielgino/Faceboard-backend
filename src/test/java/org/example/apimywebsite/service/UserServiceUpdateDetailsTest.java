package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.User;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Multi-field PATCH regression: the reported bug ("changing first name +
 * last name together sometimes only persists one of them") turned out to be
 * a frontend concurrency issue (Settings.js fired one independent PUT
 * /user/settings request per edited field via Promise.all, and those
 * requests race each other's non-transactional read-modify-write on this
 * same User row). UserService.updateUserDetails itself was never buggy, but
 * no test previously exercised its "several fields set together on one DTO"
 * path directly - this locks in the invariant the frontend fix now relies
 * on: a single call with multiple fields set persists all of them and
 * leaves every field NOT present on the DTO (null) untouched.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceUpdateDetailsTest {

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

        lenient().when(friendshipService.getAcceptedFriends(any())).thenReturn(List.of());
        lenient().when(userMapper.toUserDTOWithFriends(any(), any())).thenReturn(UserDTO.builder().build());
    }

    @Test
    void updateUserDetails_multipleFieldsSetOnOneDto_persistsAllOfThem() {
        User existing = User.builder()
                .id(1)
                .name("OldFirst")
                .lastname("OldLast")
                .bio("old bio")
                .email("old@example.com")
                .build();
        when(userRepository.findById(1)).thenReturn(Optional.of(existing));

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setName("NewFirst");
        dto.setLastname("NewLast");

        userService.updateUserDetails(1, dto);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();

        assertEquals("Newfirst", saved.getName());
        assertEquals("Newlast", saved.getLastname());
        // Fields absent from the DTO (null) must be left exactly as they
        // were - a multi-field PATCH must never null out unrelated data.
        assertEquals("old bio", saved.getBio());
        assertEquals("old@example.com", saved.getEmail());
    }

    @Test
    void updateUserDetails_onlyOneFieldSetOnDto_leavesEveryOtherFieldUntouched() {
        User existing = User.builder()
                .id(1)
                .name("OldFirst")
                .lastname("OldLast")
                .bio("old bio")
                .email("old@example.com")
                .facebookUrl("https://facebook.com/old")
                .build();
        when(userRepository.findById(1)).thenReturn(Optional.of(existing));

        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setBio("new bio");

        userService.updateUserDetails(1, dto);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();

        assertEquals("new bio", saved.getBio());
        assertEquals("OldFirst", saved.getName());
        assertEquals("OldLast", saved.getLastname());
        assertEquals("old@example.com", saved.getEmail());
        assertEquals("https://facebook.com/old", saved.getFacebookUrl());
    }
}

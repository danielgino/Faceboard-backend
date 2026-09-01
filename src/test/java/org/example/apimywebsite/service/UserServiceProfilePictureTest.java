package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Verifies that a failed profile-picture upload cannot corrupt or remove the
 * user's existing valid picture (C4 requirement #6/#7: consistency on failure).
 * This exercises pre-existing UserService logic, unmodified by the C4 authorization fix.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceProfilePictureTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryService cloudinaryService;

    private UserService userService;

    private static final int USER_A = 1;
    private static final String EXISTING_URL = "https://cdn.example.com/existing.png";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, cloudinaryService);
    }

    private MultipartFile aFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    void uploadProfilePicture_success_updatesAndPersistsNewUrl() throws IOException {
        User user = User.builder().id(USER_A).profilePictureUrl(EXISTING_URL).build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(user));
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/new.png");

        String result = userService.uploadProfilePicture(USER_A, aFile());

        assertEquals("https://cdn.example.com/new.png", result);
        assertEquals("https://cdn.example.com/new.png", user.getProfilePictureUrl());
        verify(userRepository).save(user);
    }

    @Test
    void uploadProfilePicture_whenStorageUploadFails_leavesExistingPictureIntact() throws IOException {
        User user = User.builder().id(USER_A).profilePictureUrl(EXISTING_URL).build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(user));
        when(cloudinaryService.uploadImage(any())).thenThrow(new IOException("storage unavailable"));

        assertThrows(IOException.class, () -> userService.uploadProfilePicture(USER_A, aFile()));

        // The user's picture must remain exactly as it was before the failed attempt,
        // and the failed upload must never reach persistence.
        assertEquals(EXISTING_URL, user.getProfilePictureUrl());
        verify(userRepository, never()).save(any());
        // Cloudinary orphan-image cleanup: a failed upload must never trigger deletion of the
        // still-current old picture.
        verify(cloudinaryService, never()).deleteImage(any());
    }

    // ---- Cloudinary orphan-image cleanup ----

    @Test
    void uploadProfilePicture_success_deletesThePreviousCloudinaryImage_onlyAfterTheNewOneIsSaved() throws IOException {
        User user = User.builder().id(USER_A).profilePictureUrl(EXISTING_URL).build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(user));
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/new.png");

        userService.uploadProfilePicture(USER_A, aFile());

        org.mockito.InOrder order = inOrder(userRepository, cloudinaryService);
        order.verify(userRepository).save(user);
        order.verify(cloudinaryService).deleteImage(EXISTING_URL);
    }

    // COR-010 fix: if persisting the new picture fails, the just-uploaded new asset is the one
    // that becomes orphaned (nothing will ever reference it) - it must be deleted, while the
    // still-current old picture is left completely untouched.
    @Test
    void uploadProfilePicture_whenSaveFails_deletesTheNewlyUploadedImage_leavesOldImageIntact() throws IOException {
        User user = User.builder().id(USER_A).profilePictureUrl(EXISTING_URL).build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(user));
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/new.png");
        when(userRepository.save(user)).thenThrow(new RuntimeException("db connection reset"));

        assertThrows(RuntimeException.class, () -> userService.uploadProfilePicture(USER_A, aFile()));

        verify(cloudinaryService).deleteImage("https://cdn.example.com/new.png");
        verify(cloudinaryService, never()).deleteImage(EXISTING_URL);
    }

    @Test
    void removeProfilePicture_resetsToDefault_andDeletesThePreviousCloudinaryImage() {
        User user = User.builder().id(USER_A).gender(org.example.apimywebsite.enums.Gender.FEMALE)
                .profilePictureUrl(EXISTING_URL).build();
        when(userRepository.findById(USER_A)).thenReturn(Optional.of(user));

        userService.removeProfilePicture(USER_A);

        assertEquals(org.example.apimywebsite.util.Constants.DEFAULT_PROFILE_PICTURE_FEMALE, user.getProfilePictureUrl());
        verify(userRepository).save(user);
        verify(cloudinaryService).deleteImage(EXISTING_URL);
    }
}

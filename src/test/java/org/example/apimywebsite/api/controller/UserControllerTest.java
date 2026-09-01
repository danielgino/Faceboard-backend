package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.UpdateUserDTO;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for UserController's profile-picture endpoints (C4 fix).
 * Scenario convention: A = legitimate owner, C = unauthorized third party.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private UserController controller;

    private static final int USER_A = 1;
    private static final int USER_C = 3;

    private void loginAs(int userId) {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(userId).build());
    }

    private MultipartFile aFile() {
        return new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});
    }

    // ---- upload ----

    @Test
    void uploadProfilePicture_forSelf_succeeds() throws Exception {
        loginAs(USER_A);
        MultipartFile file = aFile();
        when(userService.uploadProfilePicture(eq(USER_A), any())).thenReturn("https://cdn.example.com/new.png");

        ResponseEntity<String> response = controller.uploadProfilePicture(USER_A, file);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("https://cdn.example.com/new.png"));
        verify(userService).uploadProfilePicture(USER_A, file);
    }

    @Test
    void uploadProfilePicture_replacingExisting_succeeds() throws Exception {
        // Same endpoint is used to replace an already-set picture; verify it still works
        // the second time (i.e. the flow is not "create once" only).
        loginAs(USER_A);
        when(userService.uploadProfilePicture(eq(USER_A), any()))
                .thenReturn("https://cdn.example.com/first.png")
                .thenReturn("https://cdn.example.com/second.png");

        ResponseEntity<String> first = controller.uploadProfilePicture(USER_A, aFile());
        ResponseEntity<String> second = controller.uploadProfilePicture(USER_A, aFile());

        assertEquals(200, first.getStatusCode().value());
        assertEquals(200, second.getStatusCode().value());
        assertTrue(second.getBody().contains("https://cdn.example.com/second.png"));
        verify(userService, times(2)).uploadProfilePicture(eq(USER_A), any());
    }

    @Test
    void uploadProfilePicture_forAnotherUser_isForbidden() throws Exception {
        // C tries to upload/replace A's profile picture by supplying A's id in the path.
        loginAs(USER_C);

        ResponseEntity<String> response = controller.uploadProfilePicture(USER_A, aFile());

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(userService);
    }

    // ---- remove ----

    @Test
    void removeProfilePicture_forSelf_succeeds() {
        loginAs(USER_A);

        ResponseEntity<String> response = controller.removeProfilePicture(USER_A);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).removeProfilePicture(USER_A);
    }

    @Test
    void removeProfilePicture_forAnotherUser_isForbidden() {
        loginAs(USER_C);

        ResponseEntity<String> response = controller.removeProfilePicture(USER_A);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(userService);
    }

    // ---- getUser (/user/id) - public-profile finding: self-only, DTO not raw entity ----

    @Test
    void getUser_forSelf_returnsUserDTO_viaGetUserDTOById_notRawEntityLookup() {
        // 1000 is outside the cached Integer range (-128..127): a reference-equality mistake
        // in the self-check (e.g. "getId() != id" evaluated as boxed reference comparison)
        // would fail this test where it would silently pass for small ids.
        int selfId = 1000;
        loginAs(selfId);
        UserDTO dto = UserDTO.builder().id(selfId).username("alice").build();
        when(userService.getUserDTOById(selfId)).thenReturn(dto);

        ResponseEntity<?> response = controller.getUser(selfId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(dto, response.getBody());
        verify(userService).getUserDTOById(selfId);
        // Never falls back to a raw-entity lookup (UserService.getUser returns the bare User
        // entity, which is exactly what this endpoint used to leak).
        verify(userService, never()).getUser(any(Integer.class));
    }

    @Test
    void getUser_forAnotherUser_isForbidden_andNeverReachesUserService() {
        loginAs(USER_C);

        ResponseEntity<?> response = controller.getUser(USER_A);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(userService);
    }

    // ---- updateProfile (/user/settings) - M-OOP1: acting user now resolved via AuthHelper
    // instead of duplicating SecurityContextHolder access ----

    @Test
    void updateProfile_usesAuthHelperCurrentUser_updatesThatUsersOwnSettings() {
        loginAs(USER_A);
        UpdateUserDTO dto = new UpdateUserDTO();
        UserDTO updated = UserDTO.builder().id(USER_A).build();
        when(userService.updateUserDetails(USER_A, dto)).thenReturn(updated);

        ResponseEntity<UserDTO> response = controller.updateProfile(dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(updated, response.getBody());
        verify(userService).updateUserDetails(USER_A, dto);
    }
}

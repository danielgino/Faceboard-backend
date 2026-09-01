package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendRequestDTO;
import org.example.apimywebsite.service.FriendshipService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for FriendshipController (C1 fix).
 * Scenario convention: A = legitimate initiator, B = legitimate recipient/owner, C = unauthorized third party.
 */
@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    @Mock
    private FriendshipService friendshipService;
    @Mock
    private UserService userService;
    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private FriendshipController controller;

    private static final int USER_A = 1;
    private static final int USER_B = 2;
    private static final int USER_C = 3;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = User.builder().id(USER_A).build();
        userB = User.builder().id(USER_B).build();
    }

    private void loginAs(int userId) {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(userId).build());
    }

    // ---- sendFriendRequest ----

    @Test
    void sendFriendRequest_asSelf_succeeds() {
        loginAs(USER_A);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(userService.findById(USER_B)).thenReturn(userB);
        when(friendshipService.sendFriendRequest(userA, userB)).thenReturn(true);

        ResponseEntity<FriendRequestDTO> response = controller.sendFriendRequest(USER_A, USER_B);

        assertEquals(200, response.getStatusCode().value());
        verify(friendshipService).sendFriendRequest(userA, userB);
    }

    // ---- H10: the controller relies on UserService.findById's own 404 and performs no
    // incidental user saves - it must not reproduce null/id==0 checks or call save() itself ----

    @Test
    void sendFriendRequest_missingUser_propagatesServicesOwn404_notReproducedByController() {
        loginAs(USER_A);
        when(userService.findById(USER_A)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.sendFriendRequest(USER_A, USER_B));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(friendshipService);
    }

    @Test
    void sendFriendRequest_asSelf_succeeds_neverCallsUserServiceSave() {
        loginAs(USER_A);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(userService.findById(USER_B)).thenReturn(userB);
        when(friendshipService.sendFriendRequest(userA, userB)).thenReturn(true);

        controller.sendFriendRequest(USER_A, USER_B);

        verify(userService, never()).save(any());
    }

    @Test
    void sendFriendRequest_asUnrelatedThirdParty_isForbidden() {
        // C tries to send a friend request "as" A to B without being authenticated as A.
        loginAs(USER_C);

        ResponseEntity<FriendRequestDTO> response = controller.sendFriendRequest(USER_A, USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }

    // ---- acceptFriendRequest ----

    @Test
    void acceptFriendRequest_byLegitimateRecipient_succeeds() {
        // B accepts a request that was sent to B by A.
        loginAs(USER_B);
        when(userService.findById(USER_B)).thenReturn(userB);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(friendshipService.acceptFriendRequest(userB, userA)).thenReturn(new Friends());

        ResponseEntity<String> response = controller.acceptFriendRequest(USER_B, USER_A);

        assertEquals(200, response.getStatusCode().value());
        verify(friendshipService).acceptFriendRequest(userB, userA);
    }

    @Test
    void acceptFriendRequest_byNonRecipient_isForbidden() {
        // A tries to force-accept a request on B's behalf.
        loginAs(USER_A);

        ResponseEntity<String> response = controller.acceptFriendRequest(USER_B, USER_A);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }

    @Test
    void acceptFriendRequest_byUnrelatedThirdParty_isForbidden() {
        // C tries to accept a friendship between A and B.
        loginAs(USER_C);

        ResponseEntity<String> response = controller.acceptFriendRequest(USER_B, USER_A);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }

    // ---- removeFriendship ----

    @Test
    void removeFriendship_byEitherParty_succeeds() {
        loginAs(USER_B);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(userService.findById(USER_B)).thenReturn(userB);

        ResponseEntity<?> response = controller.removeFriendship(USER_A, USER_B);

        assertEquals(200, response.getStatusCode().value());
        verify(friendshipService).removeFriendship(userA, userB);
    }

    @Test
    void removeFriendship_byUnrelatedThirdParty_isForbidden() {
        loginAs(USER_C);

        ResponseEntity<?> response = controller.removeFriendship(USER_A, USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }

    // ---- declineFriendRequest ----

    @Test
    void declineFriendRequest_byLegitimateReceiver_succeeds() {
        loginAs(USER_B);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(userService.findById(USER_B)).thenReturn(userB);
        FriendRequestDTO dto = new FriendRequestDTO(USER_A, USER_B, "PENDING");

        ResponseEntity<String> response = controller.declineFriendRequest(dto);

        assertEquals(200, response.getStatusCode().value());
        verify(friendshipService).declineFriendRequest(userB, userA);
    }

    @Test
    void declineFriendRequest_byUnrelatedThirdParty_isForbidden() {
        loginAs(USER_C);
        FriendRequestDTO dto = new FriendRequestDTO(USER_A, USER_B, "PENDING");

        ResponseEntity<String> response = controller.declineFriendRequest(dto);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }

    // ---- checkStatus ----

    @Test
    void checkStatus_byParty_succeeds() {
        loginAs(USER_A);
        when(userService.findById(USER_A)).thenReturn(userA);
        when(userService.findById(USER_B)).thenReturn(userB);
        when(friendshipService.getRelationshipBetweenUsers(userA, userB))
                .thenReturn(new FriendRequestDTO(USER_A, USER_B, "ACCEPTED"));

        ResponseEntity<FriendRequestDTO> response = controller.checkStatus(USER_A, USER_B);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void checkStatus_byUnrelatedThirdParty_isForbidden() {
        loginAs(USER_C);

        ResponseEntity<FriendRequestDTO> response = controller.checkStatus(USER_A, USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(friendshipService);
    }
}

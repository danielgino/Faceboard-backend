package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.dto.SendMessageRequestDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.service.MessageService;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for MessageController (C2 fix).
 * Scenario convention: A = legitimate initiator, B = legitimate recipient/owner, C = unauthorized third party.
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private MessageController controller;

    private static final int USER_A = 1;
    private static final int USER_B = 2;
    private static final int USER_C = 3;

    private void loginAs(int userId) {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(userId).build());
    }

    // ---- sendMessage ----

    private static SendMessageRequestDTO sendRequest(int receiverId, String message) {
        SendMessageRequestDTO dto = new SendMessageRequestDTO();
        dto.setReceiverId(receiverId);
        dto.setMessage(message);
        return dto;
    }

    @Test
    void sendMessage_asSelf_succeeds() {
        // SEC-001 fix: senderId is no longer part of the request body at all - it is derived
        // solely from the authenticated principal, so there is no client-supplied senderId
        // left to check/impersonate.
        loginAs(USER_A);
        MessageDTO saved = new MessageDTO();
        when(messageService.sendMessage(USER_A, USER_B, "hi")).thenReturn(saved);

        ResponseEntity<MessageDTO> response = controller.sendMessage(sendRequest(USER_B, "hi"));

        assertEquals(200, response.getStatusCode().value());
        verify(messageService).sendMessage(USER_A, USER_B, "hi");
    }

    @Test
    void sendMessage_usesAuthenticatedSenderRegardlessOfWhoIsLoggedIn() {
        // C sends a message; the resulting senderId passed to the service must be C's own id
        // (derived from AuthHelper), never anything from the request body.
        loginAs(USER_C);
        MessageDTO saved = new MessageDTO();
        when(messageService.sendMessage(USER_C, USER_B, "hi")).thenReturn(saved);

        ResponseEntity<MessageDTO> response = controller.sendMessage(sendRequest(USER_B, "hi"));

        assertEquals(200, response.getStatusCode().value());
        verify(messageService).sendMessage(USER_C, USER_B, "hi");
    }

    // ---- sendMessage: H8b MessageParticipantNotFoundException -> 404 translation ----

    @Test
    void sendMessage_senderNotFound_translatesTo404_withExactMessage() {
        loginAs(USER_A);
        when(messageService.sendMessage(USER_A, USER_B, "hi"))
                .thenThrow(new MessageParticipantNotFoundException("Sender not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.sendMessage(sendRequest(USER_B, "hi")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Sender not found", ex.getReason());
    }

    @Test
    void sendMessage_receiverNotFound_translatesTo404_withExactMessage() {
        loginAs(USER_A);
        when(messageService.sendMessage(USER_A, USER_B, "hi"))
                .thenThrow(new MessageParticipantNotFoundException("Receiver not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.sendMessage(sendRequest(USER_B, "hi")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Receiver not found", ex.getReason());
    }

    @Test
    void sendMessage_unexpectedRuntimeException_isNotConvertedTo404_propagatesUnchanged() {
        // Only MessageParticipantNotFoundException is translated; any other RuntimeException
        // (a genuinely unexpected failure) must reach GlobalExceptionHandler's generic
        // handler unchanged, not be misclassified as a 404 - this is the exact H8c-adjacent
        // risk the narrow catch (instead of a broad RuntimeException catch) avoids.
        loginAs(USER_A);
        RuntimeException unexpected = new RuntimeException("db connection reset");
        when(messageService.sendMessage(USER_A, USER_B, "hi")).thenThrow(unexpected);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> controller.sendMessage(sendRequest(USER_B, "hi")));

        assertEquals(unexpected, thrown);
    }

    // ---- getConversation ----

    @Test
    void getConversation_byEitherParty_succeeds() {
        loginAs(USER_A);
        when(messageService.getMessagesForConversation(USER_A, USER_B, null, null)).thenReturn(Collections.emptyList());
        ResponseEntity<List<MessageDTO>> responseAsA = controller.getConversation(USER_A, USER_B, null, null);
        assertEquals(200, responseAsA.getStatusCode().value());

        loginAs(USER_B);
        ResponseEntity<List<MessageDTO>> responseAsB = controller.getConversation(USER_A, USER_B, null, null);
        assertEquals(200, responseAsB.getStatusCode().value());
    }

    @Test
    void getConversation_byUnrelatedThirdParty_isForbidden() {
        loginAs(USER_C);

        ResponseEntity<List<MessageDTO>> response = controller.getConversation(USER_A, USER_B, null, null);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(messageService);
    }

    // ---- M-DB2: page/size params reach the service unchanged, and can never bypass C2 ----

    @Test
    void getConversation_noPageParams_passesNullsThroughToService() {
        loginAs(USER_A);
        when(messageService.getMessagesForConversation(USER_A, USER_B, null, null))
                .thenReturn(Collections.emptyList());

        controller.getConversation(USER_A, USER_B, null, null);

        verify(messageService).getMessagesForConversation(USER_A, USER_B, null, null);
    }

    @Test
    void getConversation_withPageParams_passesThemThroughToServiceUnchanged() {
        loginAs(USER_A);
        when(messageService.getMessagesForConversation(USER_A, USER_B, 2, 30))
                .thenReturn(Collections.emptyList());

        controller.getConversation(USER_A, USER_B, 2, 30);

        verify(messageService).getMessagesForConversation(USER_A, USER_B, 2, 30);
    }

    @Test
    void getConversation_byUnrelatedThirdParty_isForbidden_evenWithPageParamsSupplied() {
        // Proves pagination params cannot be used to reach a conversation the caller isn't
        // part of - the C2 ownership check runs before the service (and therefore the
        // repository/pagination logic) is ever reached.
        loginAs(USER_C);

        ResponseEntity<List<MessageDTO>> response = controller.getConversation(USER_A, USER_B, 0, 1000000);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(messageService);
    }

    // ---- markMessagesAsRead ----

    @Test
    void markMessagesAsRead_byLegitimateReceiver_succeeds() {
        // B marks messages sent by A (to B) as read.
        loginAs(USER_B);

        ResponseEntity<String> response = controller.markMessagesAsRead(USER_A, USER_B);

        assertEquals(200, response.getStatusCode().value());
        verify(messageService).markMessagesAsRead(USER_A, USER_B);
    }

    @Test
    void markMessagesAsRead_bySender_isForbidden() {
        // A (the sender) tries to mark messages addressed to B as read.
        loginAs(USER_A);

        ResponseEntity<String> response = controller.markMessagesAsRead(USER_A, USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(messageService);
    }

    @Test
    void markMessagesAsRead_byUnrelatedThirdParty_isForbidden() {
        loginAs(USER_C);

        ResponseEntity<String> response = controller.markMessagesAsRead(USER_A, USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(messageService);
    }

    // ---- getUnreadSummary ----

    @Test
    void getUnreadSummary_ownData_succeeds() {
        loginAs(USER_A);
        when(messageService.getUnreadSummary(USER_A)).thenReturn(Map.of());

        ResponseEntity<Map<Integer, Long>> response = controller.getUnreadSummary(USER_A);

        assertEquals(200, response.getStatusCode().value());
        verify(messageService).getUnreadSummary(USER_A);
    }

    @Test
    void getUnreadSummary_forOtherUser_isForbidden() {
        loginAs(USER_A);

        ResponseEntity<Map<Integer, Long>> response = controller.getUnreadSummary(USER_B);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(messageService);
    }
}

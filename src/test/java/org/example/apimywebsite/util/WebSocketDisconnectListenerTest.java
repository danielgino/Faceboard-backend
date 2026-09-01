package org.example.apimywebsite.util;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for WebSocketDisconnectListener (C6 completion): the disconnecting user must be
 * derived from the server-resolved, authenticated Principal that Spring attaches to the
 * synthesized DISCONNECT message — never from a client-suppliable header (verified against
 * Spring's actual StompSubProtocolHandler.createDisconnectMessage(), which never copies
 * native CONNECT headers into the disconnect message, only sessionId/sessionAttributes/user).
 * Scenario convention: A = an unrelated legitimate user with active-chat state,
 * C = a different user whose own session disconnects.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketDisconnectListenerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private WebSocketDisconnectListener listener;

    private static final int USER_A = 1;
    private static final int USER_C = 3;
    private static final int PEER = 999;

    private Principal principalFor(String username) {
        return new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("USER")));
    }

    private SessionDisconnectEvent disconnectEventFor(Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setLeaveMutable(true);
        if (principal != null) {
            accessor.setUser(principal);
        }
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return new SessionDisconnectEvent(this, message, "session-1", CloseStatus.NORMAL, principal);
    }

    @AfterEach
    void cleanUp() {
        ActiveChatTracker.removeActiveChat(USER_A);
        ActiveChatTracker.removeActiveChat(USER_C);
    }

    @Test
    void disconnect_clearsActiveChatState_forTheDisconnectingAuthenticatedUser() {
        ActiveChatTracker.setActiveChat(USER_C, PEER);
        when(userService.findByUserName("userC")).thenReturn(User.builder().id(USER_C).userName("userC").build());

        listener.handleDisconnect(disconnectEventFor(principalFor("userC")));

        assertFalse(ActiveChatTracker.isUserInChatWith(USER_C, PEER));
    }

    @Test
    void disconnect_ofUserC_doesNotClearUserAsActiveChatState() {
        // A has unrelated active-chat state; C's own session disconnects.
        ActiveChatTracker.setActiveChat(USER_A, PEER);
        when(userService.findByUserName("userC")).thenReturn(User.builder().id(USER_C).userName("userC").build());

        listener.handleDisconnect(disconnectEventFor(principalFor("userC")));

        assertTrue(ActiveChatTracker.isUserInChatWith(USER_A, PEER));
    }

    @Test
    void disconnect_withoutAuthenticatedPrincipal_doesNothing() {
        listener.handleDisconnect(disconnectEventFor(null));

        verifyNoInteractions(userService);
    }
}

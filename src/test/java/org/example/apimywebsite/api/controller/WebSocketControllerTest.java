package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.dto.MarkAsReadDTO;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.service.MessageService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.ActiveChatTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Authorization tests for WebSocketController's @MessageMapping handlers (C2/C6 fixes —
 * sendMessage, markAsRead and activeChat must all derive the acting identity from the
 * authenticated STOMP principal, never from client-supplied payload fields).
 * Scenario convention: A = legitimate initiator (sender), B = legitimate recipient, C = unauthorized third party.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketControllerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private UserService userService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketController controller;

    private static final int USER_A = 1;
    private static final int USER_B = 2;
    private static final int USER_C = 3;

    private Principal principalOf(int userId, String username) {
        when(userService.getUserIdByUsername(username)).thenReturn(userId);
        return () -> username;
    }

    @AfterEach
    void cleanUpActiveChatState() {
        ActiveChatTracker.removeActiveChat(USER_A);
        ActiveChatTracker.removeActiveChat(USER_B);
        ActiveChatTracker.removeActiveChat(USER_C);
    }

    @Test
    void handleMarkAsRead_byLegitimateReceiver_marksAsReadForThatReceiver() {
        // B is connected (authenticated) and marks A's messages to B as read.
        MarkAsReadDTO dto = new MarkAsReadDTO();
        dto.setSenderId(USER_A);
        dto.setReceiverId(USER_B);
        Principal principal = principalOf(USER_B, "userB");

        controller.handleMarkAsRead(dto, principal);

        verify(messageService).markMessagesAsRead(USER_A, USER_B);
        verify(messagingTemplate).convertAndSend("/topic/message-read/" + USER_A, "read_by:" + USER_B);
    }

    @Test
    void handleMarkAsRead_payloadReceiverIdIsIgnored_cannotImpersonateAnotherReceiver() {
        // C is connected (authenticated as C) but forges receiverId=B in the payload,
        // attempting to mark B's received messages as read on B's behalf.
        MarkAsReadDTO dto = new MarkAsReadDTO();
        dto.setSenderId(USER_A);
        dto.setReceiverId(USER_B); // forged claim, must be ignored
        Principal principal = principalOf(USER_C, "userC");

        controller.handleMarkAsRead(dto, principal);

        // The service must only ever be called with the authenticated principal's id (C),
        // never with the forged payload receiverId (B).
        verify(messageService).markMessagesAsRead(USER_A, USER_C);
        verify(messageService, never()).markMessagesAsRead(USER_A, USER_B);
        verify(messagingTemplate).convertAndSend("/topic/message-read/" + USER_A, "read_by:" + USER_C);
    }

    // ---- handleMessage: delivery-once and sender impersonation ----

    @Test
    void handleMessage_deliversToSenderAndReceiverTopicsExactlyOnce() {
        MessageDTO payload = new MessageDTO();
        payload.setReceiverId(USER_B);
        payload.setMessage("hi");
        Principal principal = principalOf(USER_A, "userA");
        MessageDTO saved = new MessageDTO();
        when(messageService.sendMessage(USER_A, USER_B, "hi")).thenReturn(saved);

        controller.handleMessage(payload, principal);

        verify(messagingTemplate, times(1)).convertAndSend("/topic/messages/" + USER_A, saved);
        verify(messagingTemplate, times(1)).convertAndSend("/topic/messages/" + USER_B, saved);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void handleMessage_payloadSenderIdIsIgnored_cannotImpersonateAnotherSender() {
        // C is connected (authenticated as C) but forges a MessageDTO that looks like
        // it came from A; the sender must always be resolved from the principal.
        MessageDTO payload = new MessageDTO();
        payload.setSenderId(USER_A); // forged claim, must be ignored
        payload.setReceiverId(USER_B);
        payload.setMessage("hi");
        Principal principal = principalOf(USER_C, "userC");

        controller.handleMessage(payload, principal);

        verify(messageService).sendMessage(USER_C, USER_B, "hi");
        verify(messageService, never()).sendMessage(eq(USER_A), anyInt(), any());
    }

    // ---- H8b: MessageParticipantNotFoundException handling on the STOMP side ----

    @Test
    void handleMessage_serviceRejectsMessage_directInvocationThrowsDedicatedException_noMessagingTemplateInteraction() {
        // A plain Java method call bypasses Spring's message-handler dispatch entirely, so it
        // cannot exercise @MessageExceptionHandler routing - this only proves that
        // handleMessage itself does not catch/handle the exception locally, and that no
        // /topic/messages/* publish happens before/around the failure. See the dispatch test
        // below for proof the annotation actually routes this to the handler.
        MessageDTO payload = new MessageDTO();
        payload.setReceiverId(USER_B);
        payload.setMessage("hi");
        Principal principal = principalOf(USER_A, "userA");
        when(messageService.sendMessage(USER_A, USER_B, "hi"))
                .thenThrow(new MessageParticipantNotFoundException("Receiver not found"));

        MessageParticipantNotFoundException ex = assertThrows(MessageParticipantNotFoundException.class,
                () -> controller.handleMessage(payload, principal));

        assertEquals("Receiver not found", ex.getMessage());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void handleMessageParticipantNotFound_calledDirectly_sendsNothing() {
        // The handler method itself: confirms it has no messagingTemplate/messageService
        // interaction of its own (log-only, per the H8b restriction against inventing a
        // payload or destination).
        controller.handleMessageParticipantNotFound(new MessageParticipantNotFoundException("Receiver not found"));

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(messageService);
    }

    @Test
    void handleMessage_dedicatedException_isRoutedByRealAnnotationDispatch_toExceptionHandler_sendsNothing() throws Exception {
        // Unlike the direct-call test above, this exercises Spring's actual
        // @MessageMapping/@MessageExceptionHandler dispatch machinery
        // (SimpAnnotationMethodMessageHandler) to prove the annotation wiring itself is
        // correct - without starting a full (database-dependent) Spring Boot application
        // context. Only this one controller bean is registered.
        when(messageService.sendMessage(USER_A, USER_B, "hi"))
                .thenThrow(new MessageParticipantNotFoundException("Receiver not found"));
        when(userService.getUserIdByUsername("userA")).thenReturn(USER_A);

        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("webSocketController", controller);
        appContext.refresh();

        SimpAnnotationMethodMessageHandler dispatcher = new SimpAnnotationMethodMessageHandler(
                new ExecutorSubscribableChannel(),
                new ExecutorSubscribableChannel(),
                new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        dispatcher.setApplicationContext(appContext);
        dispatcher.setDestinationPrefixes(List.of("/app"));
        dispatcher.afterPropertiesSet();
        dispatcher.start();

        MessageDTO payload = new MessageDTO();
        payload.setReceiverId(USER_B);
        payload.setMessage("hi");

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId("test-session");
        headerAccessor.setSessionAttributes(new java.util.HashMap<>());
        headerAccessor.setDestination("/app/sendMessage");
        headerAccessor.setUser(() -> "userA");
        headerAccessor.setLeaveMutable(true);
        Message<MessageDTO> message = MessageBuilder.createMessage(payload, headerAccessor.getMessageHeaders());

        // If @MessageExceptionHandler did NOT route this, the MessageParticipantNotFoundException
        // thrown inside handleMessage would propagate out of handleMessage() here instead.
        dispatcher.handleMessage(message);

        verifyNoInteractions(messagingTemplate);
    }

    // ---- API-001 fix: @Valid on the /sendMessage payload, exercised via real dispatch (like
    // the MessageParticipantNotFoundException test above) since @Valid is resolved by Spring
    // Messaging's PayloadArgumentResolver, not by anything a direct Java method call triggers. ----

    @Test
    void handleMessage_blankMessagePayload_isRejectedByValidation_beforeReachingTheServiceOrBroadcasting() throws Exception {
        // No userService/messageService stubbing needed: validation must reject the payload
        // before handleMessage's body - and therefore before either mock - is ever touched.
        StaticApplicationContext appContext = new StaticApplicationContext();
        appContext.getBeanFactory().registerSingleton("webSocketController", controller);
        appContext.refresh();

        SimpAnnotationMethodMessageHandler dispatcher = new SimpAnnotationMethodMessageHandler(
                new ExecutorSubscribableChannel(),
                new ExecutorSubscribableChannel(),
                new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        dispatcher.setApplicationContext(appContext);
        dispatcher.setDestinationPrefixes(List.of("/app"));
        // Explicit, since a manually-constructed dispatcher (unlike full Spring Boot
        // auto-configuration) is not guaranteed to have a Bean Validation-backed validator
        // wired in by default - without one, @Valid resolves as a silent no-op. afterPropertiesSet()
        // must be called directly since this bean is constructed manually here, not by a
        // Spring container that would normally invoke it as part of the bean lifecycle.
        org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator =
                new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        dispatcher.setValidator(validator);
        dispatcher.afterPropertiesSet();
        dispatcher.start();

        MessageDTO payload = new MessageDTO();
        payload.setReceiverId(USER_B);
        payload.setMessage("   "); // blank: must fail @NotBlank

        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headerAccessor.setSessionId("test-session");
        headerAccessor.setSessionAttributes(new java.util.HashMap<>());
        headerAccessor.setDestination("/app/sendMessage");
        headerAccessor.setUser(() -> "userA");
        headerAccessor.setLeaveMutable(true);
        Message<MessageDTO> message = MessageBuilder.createMessage(payload, headerAccessor.getMessageHeaders());

        // If @Valid/@MessageExceptionHandler did not route this cleanly, either the blank
        // message would reach messageService.sendMessage below, or the validation exception
        // would propagate out of dispatcher.handleMessage instead of being handled.
        dispatcher.handleMessage(message);

        verifyNoInteractions(userService);
        verifyNoInteractions(messageService);
        verifyNoInteractions(messagingTemplate);
    }

    // ---- activeChat: authenticated identity, not payload ----

    @Test
    void updateActiveChat_setsActiveChatForAuthenticatedUser() {
        WebSocketController.ChatStatusUpdate update = new WebSocketController.ChatStatusUpdate();
        update.setUserId(999); // irrelevant/forged; must be ignored
        update.setChattingWith(USER_B);
        Principal principal = principalOf(USER_A, "userA");

        controller.updateActiveChat(update, principal);

        assertTrue(ActiveChatTracker.isUserInChatWith(USER_A, USER_B));
    }

    @Test
    void updateActiveChat_payloadUserIdIsIgnored_cannotImpersonateAnotherUser() {
        // C claims to be A in the payload; the active-chat entry must be recorded for C, not A.
        WebSocketController.ChatStatusUpdate update = new WebSocketController.ChatStatusUpdate();
        update.setUserId(USER_A); // forged claim, must be ignored
        update.setChattingWith(USER_B);
        Principal principal = principalOf(USER_C, "userC");

        controller.updateActiveChat(update, principal);

        assertTrue(ActiveChatTracker.isUserInChatWith(USER_C, USER_B));
        assertFalse(ActiveChatTracker.isUserInChatWith(USER_A, USER_B));
    }
}

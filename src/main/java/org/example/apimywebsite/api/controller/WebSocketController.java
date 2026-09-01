package org.example.apimywebsite.api.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.dto.MarkAsReadDTO;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.exception.UnauthenticatedStompException;
import org.example.apimywebsite.service.MessageService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.ActiveChatTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;



    // SEC-004 fix: JwtChannelInterceptor now rejects the CONNECT itself when the Authorization
    // header is missing or invalid, so an anonymous session with `principal == null` reaching a
    // @MessageMapping here should no longer be reachable at all. This guard is kept as an
    // explicit, intentional defense-in-depth check rather than relying solely on the interceptor -
    // without it, a future change to the interceptor's CONNECT policy could silently reopen the
    // same anonymous-send/impersonation risk this was originally written to close.
    private void requireAuthenticated(Principal principal) {
        if (principal == null) {
            throw new UnauthenticatedStompException("STOMP command requires an authenticated session");
        }
    }

    @MessageExceptionHandler(UnauthenticatedStompException.class)
    public void handleUnauthenticatedStomp(UnauthenticatedStompException ex) {
        log.warn("STOMP command rejected: {}", ex.getMessage());
    }

    // API-001 fix: @Valid triggers the same Bean Validation constraints now declared on
    // MessageDTO (@NotBlank message, @Positive receiverId) for this STOMP payload, matching
    // what SendMessageRequestDTO already enforces on the REST /messages/send path - previously
    // only REST rejected a null/blank message up front. A validation failure throws Spring
    // Messaging's own MethodArgumentNotValidException, handled below the same way every other
    // expected rejection in this controller is: logged, no STOMP ERROR frame.
    @MessageMapping("/sendMessage")
    public void handleMessage(@Valid @Payload MessageDTO messageDTO, Principal principal) {
        requireAuthenticated(principal);
        String username = principal.getName();
        int senderId = userService.getUserIdByUsername(username);
        MessageDTO saved = messageService.sendMessage(
                senderId,
                messageDTO.getReceiverId(),
                messageDTO.getMessage()
        );
        messagingTemplate.convertAndSend("/topic/messages/" + senderId, saved);
        messagingTemplate.convertAndSend("/topic/messages/" + messageDTO.getReceiverId(), saved);
    }

    // H8b: narrowly scoped to this one expected business outcome (sender/receiver id did not
    // resolve to a user) - any other, unexpected RuntimeException from handleMessage is
    // intentionally NOT caught here and falls through to Spring's default STOMP error
    // handling unchanged. Deliberately sends nothing back to the client: no destination in
    // this app can safely carry an error payload without a frontend/broker change (out of
    // scope here), and registering this handler for this one exception type is what prevents
    // Spring's default STOMP ERROR frame - and its documented connection-ending risk - for
    // this known, already-safe validation failure. Only the fixed business message is logged
    // (no user ids, payload content, or request details).
    @MessageExceptionHandler(MessageParticipantNotFoundException.class)
    public void handleMessageParticipantNotFound(MessageParticipantNotFoundException ex) {
        log.warn("Chat message send rejected: {}", ex.getMessage());
    }

    // API-001 fix: same rationale as handleMessageParticipantNotFound above - a @Valid failure
    // on the /sendMessage payload (blank message, non-positive receiverId) is an expected,
    // already-safe rejection, not a genuinely unexpected error. Only field names are logged
    // (via getBindingResult()), never the rejected payload content.
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleInvalidMessagePayload(MethodArgumentNotValidException ex) {
        log.warn("Chat message send rejected: invalid payload ({})",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fieldError -> fieldError.getField())
                        .toList());
    }

    @MessageMapping("/markAsRead")
    public void handleMarkAsRead(@Payload MarkAsReadDTO dto, Principal principal) {
        requireAuthenticated(principal);
        int receiverId = userService.getUserIdByUsername(principal.getName());
        messageService.markMessagesAsRead(dto.getSenderId(), receiverId);
        messagingTemplate.convertAndSend(
                "/topic/message-read/" + dto.getSenderId(),
                "read_by:" + receiverId
        );
    }



    @MessageMapping("/activeChat")
    public void updateActiveChat(@Payload ChatStatusUpdate update, Principal principal) {
        requireAuthenticated(principal);
        int userId = userService.getUserIdByUsername(principal.getName());
        if (update.getChattingWith() != null) {
            ActiveChatTracker.setActiveChat(userId, update.getChattingWith());
        } else {
            ActiveChatTracker.removeActiveChat(userId);
        }
    }

    public static class ChatStatusUpdate {
        private Integer userId;
        private Integer chattingWith;

        public Integer getUserId() { return userId; }
        public void setUserId(Integer userId) { this.userId = userId; }
        public Integer getChattingWith() { return chattingWith; }
        public void setChattingWith(Integer chattingWith) { this.chattingWith = chattingWith; }
    }


}

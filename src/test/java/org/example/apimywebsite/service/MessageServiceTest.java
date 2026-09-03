package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H8b: MessageService.getMessagesForConversation (REST-only - MessageController) throws a
 * typed ResponseStatusException(404). sendMessage (shared REST + WebSocket/STOMP) throws the
 * dedicated, transport-agnostic MessageParticipantNotFoundException instead - each transport
 * (MessageController / WebSocketController) translates it to its own appropriate response at
 * its own boundary; see those classes and the H8b final report for why.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    // Demo Mode: getMessagesForConversation now resolves the caller via AuthHelper for its
    // demo-scoping check. Left unstubbed in every existing test here deliberately -
    // DemoScope.assertAccessible(null, ...) no-ops safely for a null/non-demo caller, so these
    // pre-existing non-demo-focused tests need no behavior change beyond this mock existing
    // (without it, @InjectMocks would leave the field null and NPE on the first call).
    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private MessageService messageService;

    @Test
    void getMessagesForConversation_userNotFound_throwsNotFound() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesForConversation(1, 2, null, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
    }

    @Test
    void getMessagesForConversation_otherUserNotFound_throwsNotFound() {
        when(userRepository.findById(1)).thenReturn(Optional.of(User.builder().id(1).build()));
        when(userRepository.findById(2)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesForConversation(1, 2, null, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Other user not found", ex.getReason());
    }

    // ---- M-DB2: bounded conversation pagination ----

    private void stubBothUsersFound(int userId, int otherUserId) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(User.builder().id(otherUserId).build()));
    }

    private Message aMessage(int id, int senderId, int receiverId, OffsetDateTime sentTime) {
        Message m = new Message();
        m.setId(id);
        m.setSender(User.builder().id(senderId).build());
        m.setReceiver(User.builder().id(receiverId).build());
        m.setMessage("msg-" + id);
        m.setSentTime(sentTime);
        return m;
    }

    @Test
    void getMessagesForConversation_noPageOrSizeGiven_defaultsToPageZeroAndDefaultSize() {
        stubBothUsersFound(1, 2);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        messageService.getMessagesForConversation(1, 2, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findMessagesBetweenUsers(eq(1), eq(2), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(MessageService.DEFAULT_CONVERSATION_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMessagesForConversation_requestedPageAndSize_arePassedThroughToRepository() {
        stubBothUsersFound(1, 2);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        messageService.getMessagesForConversation(1, 2, 2, 30);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findMessagesBetweenUsers(eq(1), eq(2), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(30, captor.getValue().getPageSize());
    }

    @Test
    void getMessagesForConversation_sizeAboveMax_isClampedToMax_cannotRecreateUnboundedQuery() {
        stubBothUsersFound(1, 2);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        messageService.getMessagesForConversation(1, 2, 0, Integer.MAX_VALUE);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findMessagesBetweenUsers(eq(1), eq(2), captor.capture());
        assertEquals(MessageService.MAX_CONVERSATION_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMessagesForConversation_negativePageAndSize_areNormalizedToDefaults() {
        stubBothUsersFound(1, 2);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        messageService.getMessagesForConversation(1, 2, -5, -10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findMessagesBetweenUsers(eq(1), eq(2), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(MessageService.DEFAULT_CONVERSATION_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMessagesForConversation_zeroSize_treatedAsDefaultNotAnEmptyPage() {
        stubBothUsersFound(1, 2);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        messageService.getMessagesForConversation(1, 2, 0, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findMessagesBetweenUsers(eq(1), eq(2), captor.capture());
        assertEquals(MessageService.DEFAULT_CONVERSATION_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getMessagesForConversation_repositoryDescPage_isReversedToChronologicalAscInResponse() {
        // The repository query orders sentTime DESC (most-recent-first page); the service must
        // reverse it before returning so the response stays oldest-first, matching what the
        // frontend already assumes (last array element = most recent message).
        stubBothUsersFound(1, 2);
        OffsetDateTime t1 = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t2 = OffsetDateTime.of(2026, 1, 1, 10, 1, 0, 0, ZoneOffset.UTC);
        OffsetDateTime t3 = OffsetDateTime.of(2026, 1, 1, 10, 2, 0, 0, ZoneOffset.UTC);
        // Simulate the DESC-ordered page the repository would actually return: newest first.
        Message newest = aMessage(3, 1, 2, t3);
        Message middle = aMessage(2, 2, 1, t2);
        Message oldest = aMessage(1, 1, 2, t1);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any()))
                .thenReturn(List.of(newest, middle, oldest));

        List<MessageDTO> result = messageService.getMessagesForConversation(1, 2, null, null);

        assertEquals(List.of(1, 2, 3), result.stream().map(MessageDTO::getId).toList());
        assertEquals(t1, result.get(0).getSentTime());
        assertEquals(t3, result.get(2).getSentTime());
    }

    // ---- Demo Mode: both participants must be isDemo=true for a ROLE_DEMO caller ----

    @Test
    void getMessagesForConversation_demoCaller_bothParticipantsDemo_isAllowed() {
        User demoUser = User.builder().id(1).demo(true).build();
        User demoFriend = User.builder().id(2).demo(true).build();
        when(userRepository.findById(1)).thenReturn(Optional.of(demoUser));
        when(userRepository.findById(2)).thenReturn(Optional.of(demoFriend));
        when(authHelper.getCurrentUser()).thenReturn(demoUser);
        when(messageRepository.findMessagesBetweenUsers(eq(1), eq(2), any())).thenReturn(List.of());

        List<MessageDTO> result = messageService.getMessagesForConversation(1, 2, null, null);

        assertEquals(List.of(), result);
    }

    @Test
    void getMessagesForConversation_demoCaller_otherParticipantNotDemo_isDenied() {
        // Simulates a demo token manually changed to point at a real (non-demo) user's id -
        // must never expose that conversation's content, even though the controller's own
        // ownership check (caller is one of {userId, otherUserId}) is already satisfied.
        User demoUser = User.builder().id(1).demo(true).build();
        User realUser = User.builder().id(2).demo(false).build();
        when(userRepository.findById(1)).thenReturn(Optional.of(demoUser));
        when(userRepository.findById(2)).thenReturn(Optional.of(realUser));
        when(authHelper.getCurrentUser()).thenReturn(demoUser);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> messageService.getMessagesForConversation(1, 2, null, null));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(messageRepository, never()).findMessagesBetweenUsers(anyInt(), anyInt(), any());
    }

    // ---- sendMessage (shared REST+STOMP site): dedicated, transport-agnostic exception ----

    @Test
    void sendMessage_senderNotFound_throwsMessageParticipantNotFoundException_andNeverSaves() {
        when(userRepository.findById(1)).thenReturn(Optional.empty());

        MessageParticipantNotFoundException ex = assertThrows(MessageParticipantNotFoundException.class,
                () -> messageService.sendMessage(1, 2, "hi"));

        assertEquals("Sender not found", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_receiverNotFound_throwsMessageParticipantNotFoundException_andNeverSaves() {
        when(userRepository.findById(1)).thenReturn(Optional.of(User.builder().id(1).build()));
        when(userRepository.findById(2)).thenReturn(Optional.empty());

        MessageParticipantNotFoundException ex = assertThrows(MessageParticipantNotFoundException.class,
                () -> messageService.sendMessage(1, 2, "hi"));

        assertEquals("Receiver not found", ex.getMessage());
        verify(messageRepository, never()).save(any());
    }
}

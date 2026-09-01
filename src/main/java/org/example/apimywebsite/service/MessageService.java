package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.ActiveChatTracker;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // H8b: uses a dedicated, transport-agnostic MessageParticipantNotFoundException (not
    // ResponseStatusException) because this method is shared by a REST endpoint
    // (MessageController.sendMessage) and a STOMP @MessageMapping handler
    // (WebSocketController.handleMessage), which need different responses to the same
    // failure - REST translates it to a 404 ResponseStatusException at its own boundary;
    // STOMP handles it via a local @MessageExceptionHandler. Neither transport-specific
    // concern belongs in this shared service method.
    public MessageDTO sendMessage(int senderId, int receiverId, String messageContent) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new MessageParticipantNotFoundException("Sender not found"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new MessageParticipantNotFoundException("Receiver not found"));
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMessage(messageContent);
        message.setSentTime(OffsetDateTime.now(ZoneOffset.UTC));

        if (ActiveChatTracker.isUserInChatWith(receiverId, senderId)) {
            message.setRead(true);
        }
        Message savedMessage = messageRepository.save(message);
        return convertToDTO(savedMessage);
    }

    // M-DB2: bounded, page-based load instead of the old unbounded findConversationBetweenUsers
    // (removed - no longer any caller). Reuses the repository's existing findMessagesBetweenUsers,
    // which queries sentTime DESC (most-recent page first) so the database only ever has to walk
    // the newest `size` rows; the DESC page is then reversed here so the response stays
    // chronological ASC, matching the contract the frontend already relies on (oldest-to-newest,
    // last array element = most recent message).
    public static final int DEFAULT_CONVERSATION_PAGE_SIZE = 50;
    public static final int MAX_CONVERSATION_PAGE_SIZE = 100;

    // REST-only (never called from WebSocketController), so unlike sendMessage above this
    // is safe to migrate: MessageController.getConversation has no WebSocket counterpart.
    public List<MessageDTO> getMessagesForConversation(int userId, int otherUserId, Integer page, Integer size) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Other user not found"));

        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0)
                ? DEFAULT_CONVERSATION_PAGE_SIZE
                : Math.min(size, MAX_CONVERSATION_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        List<Message> conversationMessages = new ArrayList<>(messageRepository
                .findMessagesBetweenUsers(user.getId(), otherUser.getId(), pageable));
        Collections.reverse(conversationMessages);

        return conversationMessages.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private MessageDTO convertToDTO(Message message) {
        return new MessageDTO(
                message.getId(),
                message.getMessage(),
                message.getSentTime(),
                message.getSender().getId(),
                message.getReceiver().getId(),
                message.isRead()
        );
    }


    @Transactional
    public void markMessagesAsRead(int senderId, int receiverId) {
        if (!ActiveChatTracker.isUserInChatWith(receiverId, senderId)) {
            return;
        }
        List<Message> unreadMessages = messageRepository
                .findBySenderIdAndReceiverIdAndIsReadFalse(senderId, receiverId);

        for (Message message : unreadMessages) {
            message.setRead(true);
        }
        messageRepository.saveAll(unreadMessages);
        List<MessageDTO> updatedDTOs = unreadMessages.stream()
                .map(this::convertToDTO)
                .toList();
        messagingTemplate.convertAndSend("/topic/messages/" + senderId, updatedDTOs);
    }


    public Map<Integer, Long> getUnreadSummary(int userId) {
        return messageRepository.countUnreadBySenderGrouped(userId)
                .stream()
                .collect(Collectors.toMap(
                        r -> ((Number) r[0]).intValue(),   // senderId
                        r -> ((Number) r[1]).longValue()   // count
                ));
    }

}

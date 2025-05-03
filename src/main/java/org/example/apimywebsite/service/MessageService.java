package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.repository.MessageRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.ActiveChatTracker;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private MessageDTO toDTO(Message message) {
        return new MessageDTO(
                message.getId(),
                message.getMessage(),
                message.getSentTime(),
                message.getSender().getId(),
                message.getReceiver().getId(),
                message.isRead()
        );
    }
    public MessageDTO sendMessage(int senderId, int receiverId, String messageContent) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));
        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMessage(messageContent);
        message.setSentTime(OffsetDateTime.now(ZoneOffset.UTC));


        if (ActiveChatTracker.isUserInChatWith(receiverId, senderId)) {
            System.out.println("📊 ActiveChatTracker state: " + ActiveChatTracker.getAll());

            message.setRead(true);
            System.out.println("✅ marking message as READ immediately");
        } else {
            System.out.println("ℹ️ message will be UNREAD");
        }
        Message savedMessage = messageRepository.save(message);
        return convertToDTO(savedMessage);
    }

    public List<MessageDTO> getMessagesForConversation(int userId, int otherUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("Other user not found"));

        List<Message> conversationMessages = messageRepository
                .findConversationBetweenUsers(user.getId(), otherUser.getId());

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

    public void saveMessage(MessageDTO messageDTO) {
        User sender = userRepository.findById(messageDTO.getSenderId()).orElseThrow();
        User receiver = userRepository.findById(messageDTO.getReceiverId()).orElseThrow();

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMessage(messageDTO.getMessage());
        message.setSentTime(OffsetDateTime.now(ZoneOffset.UTC));

        messageRepository.save(message);
        System.out.println("✅ Message saved in DB: " + message);
    }

    @Transactional
    public void markMessagesAsRead(int senderId, int receiverId) {
        if (!ActiveChatTracker.isUserInChatWith(receiverId, senderId)) {
            System.out.println("❗ " + receiverId + " is not in active chat with " + senderId + " – skipping mark as read");
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
}

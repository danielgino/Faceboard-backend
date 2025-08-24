package org.example.apimywebsite.api.controller;

import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.dto.MarkAsReadDTO;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.service.MessageService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.ActiveChatTracker;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;



    @MessageMapping("/sendMessage")
    public void handleMessage(@Payload MessageDTO messageDTO, Principal principal) {
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

    @MessageMapping("/markAsRead")
    public void handleMarkAsRead(@Payload MarkAsReadDTO dto) {
        messageService.markMessagesAsRead(dto.getSenderId(), dto.getReceiverId());
        messagingTemplate.convertAndSend(
                "/topic/message-read/" + dto.getSenderId(),
                "read_by:" + dto.getReceiverId()
        );
    }



    @MessageMapping("/activeChat")
    public void updateActiveChat(@Payload ChatStatusUpdate update) {
        if (update.getChattingWith() != null) {
            ActiveChatTracker.setActiveChat(update.getUserId(), update.getChattingWith());
        } else {
            ActiveChatTracker.removeActiveChat(update.getUserId());
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

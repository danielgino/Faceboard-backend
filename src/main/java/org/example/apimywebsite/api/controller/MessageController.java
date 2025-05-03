package org.example.apimywebsite.api.controller;


import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    public ResponseEntity<MessageDTO> sendMessage(@RequestParam int senderId,
                                                  @RequestParam int receiverId,
                                                  @RequestParam String message) {
        MessageDTO savedMessage = messageService.sendMessage(senderId, receiverId, message);
        return ResponseEntity.ok(savedMessage);
    }
    @PutMapping("/messages/mark-as-read")
    public ResponseEntity<String> markMessagesAsRead(@RequestParam int senderId,
                                                     @RequestParam int receiverId) {
        messageService.markMessagesAsRead(senderId, receiverId);
        return ResponseEntity.ok("Messages marked as read");
    }
    @GetMapping("/conversation/{userId}/{otherUserId}")
    public ResponseEntity<List<MessageDTO>> getConversation(
            @PathVariable int userId,
            @PathVariable int otherUserId
    ) {
        List<MessageDTO> conversation = messageService.getMessagesForConversation(userId, otherUserId);
        return ResponseEntity.ok(conversation);
    }

}
package org.example.apimywebsite.api.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.MessageDTO;
import org.example.apimywebsite.dto.SendMessageRequestDTO;
import org.example.apimywebsite.exception.MessageParticipantNotFoundException;
import org.example.apimywebsite.service.MessageService;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final AuthHelper authHelper;

    @PostMapping("/send")
    public ResponseEntity<MessageDTO> sendMessage(@Valid @RequestBody SendMessageRequestDTO request) {
        User currentUser = authHelper.getCurrentUser();

        try {
            MessageDTO savedMessage = messageService.sendMessage(currentUser.getId(), request.getReceiverId(), request.getMessage());
            return ResponseEntity.ok(savedMessage);
        } catch (MessageParticipantNotFoundException e) {
            // H8b: narrowly scoped to this one expected business outcome - unlike the
            // broader H8b REST migrations, MessageService.sendMessage is intentionally left
            // throwing a plain, non-ResponseStatusException type (shared with the STOMP
            // path), so the REST boundary translates only this specific, known exception.
            // Any other RuntimeException still propagates unchanged to GlobalExceptionHandler.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
    @PutMapping("/messages/mark-as-read")
    public ResponseEntity<String> markMessagesAsRead(@RequestParam int senderId,
                                                     @RequestParam int receiverId) {
        User currentUser = authHelper.getCurrentUser();
        if (currentUser.getId() != receiverId) {
            return ResponseEntity.status(403).body("You are not authorized to mark these messages as read.");
        }

        messageService.markMessagesAsRead(senderId, currentUser.getId());
        return ResponseEntity.ok("Messages marked as read");
    }

    @GetMapping("/unread-summary/{userId}")
    public ResponseEntity<Map<Integer, Long>> getUnreadSummary(@PathVariable int userId) {
        if (authHelper.getCurrentUser().getId() != userId) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(messageService.getUnreadSummary(userId));
    }


    // M-DB2: page/size are optional so existing callers that omit them keep working - the
    // service applies bounded defaults/max. Authorization is checked before any pagination
    // logic runs, so these params can never be used to reach another user's conversation.
    @GetMapping("/conversation/{userId}/{otherUserId}")
    public ResponseEntity<List<MessageDTO>> getConversation(
            @PathVariable int userId,
            @PathVariable int otherUserId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        int currentUserId = authHelper.getCurrentUser().getId();
        if (currentUserId != userId && currentUserId != otherUserId) {
            return ResponseEntity.status(403).build();
        }

        List<MessageDTO> conversation = messageService.getMessagesForConversation(userId, otherUserId, page, size);
        return ResponseEntity.ok(conversation);
    }

}
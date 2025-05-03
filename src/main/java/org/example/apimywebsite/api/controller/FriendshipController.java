package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.api.model.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendRequestDTO;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.example.apimywebsite.service.FriendshipService;
import org.example.apimywebsite.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/friendship")
public class FriendshipController {

    @Autowired
    private FriendshipService friendshipService;
    @Autowired
    private UserService userService;

    @PostMapping("/send/{userId}/{friendId}")
    public ResponseEntity<FriendRequestDTO> sendFriendRequest(@PathVariable int userId, @PathVariable int friendId) {
        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        if (user == null || friend == null) {
            return ResponseEntity.status(404).build();
        }

        if (user.getId() == 0) {
            user = userService.save(user);
        }
        if (friend.getId() == 0) {
            friend = userService.save(friend);
        }

        boolean requestSent = friendshipService.sendFriendRequest(user, friend);
        if (!requestSent) {
            return ResponseEntity.status(400).build();
        }
         FriendRequestDTO response = new FriendRequestDTO(user.getId(), friend.getId(),FriendshipStatus.PENDING.toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/accept/{userId}/{friendId}")
    public ResponseEntity<String> acceptFriendRequest(@PathVariable int userId, @PathVariable int friendId) {
        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        if (user == null || friend == null) {
            return ResponseEntity.status(404).body("User not found");
        }

        Friends friends = friendshipService.acceptFriendRequest(user, friend);
        if (friends == null) {
            return ResponseEntity.status(400).body("Friend request not found or already accepted.");
        }

        return ResponseEntity.ok("Friendship accepted.");
    }

    // ביטול חברות
    @DeleteMapping("/remove/{userId}/{friendId}")
    public ResponseEntity<?> removeFriendship(@PathVariable int userId, @PathVariable int friendId) {
        User user = userService.findById(userId);
        User friend = userService.findById(friendId);
        friendshipService.removeFriend(user, friend);
        friendshipService.removeFriend(friend, user);
        return ResponseEntity.ok("Friendship removed.");
    }
    @PostMapping("/decline")
    public ResponseEntity<String> declineFriendRequest(@RequestBody FriendRequestDTO requestDTO) {
        User sender = userService.findById(requestDTO.getSenderId());
        User receiver = userService.findById(requestDTO.getReceiverId());

        friendshipService.declineFriendRequest(receiver, sender);

        return ResponseEntity.ok("Friend request declined.");
    }
    @GetMapping("/status/{userId}/{friendId}")
    public ResponseEntity<FriendRequestDTO> checkStatus(@PathVariable int userId, @PathVariable int friendId) {
        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        if (user == null || friend == null) {
            return ResponseEntity.status(404).build();
        }
        FriendRequestDTO relationshipStatus = friendshipService.getRelationshipBetweenUsers(user, friend);
        return ResponseEntity.ok(relationshipStatus);
    }
}

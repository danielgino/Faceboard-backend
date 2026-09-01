package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendRequestDTO;
import org.example.apimywebsite.dto.SuggestedFriendsResponseDTO;
import org.example.apimywebsite.service.FriendshipService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/friendship")
public class FriendshipController {

    @Autowired
    private FriendshipService friendshipService;
    @Autowired
    private UserService userService;
    @Autowired
    private AuthHelper authHelper;

    @PostMapping("/send/{userId}/{friendId}")
    public ResponseEntity<FriendRequestDTO> sendFriendRequest(@PathVariable int userId, @PathVariable int friendId) {
        if (authHelper.getCurrentUser().getId() != userId) {
            return ResponseEntity.status(403).build();
        }

        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        boolean requestSent = friendshipService.sendFriendRequest(user, friend);
        if (!requestSent) {
            return ResponseEntity.status(400).build();
        }
         FriendRequestDTO response = new FriendRequestDTO(user.getId(), friend.getId(),FriendshipStatus.PENDING.toString());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/accept/{userId}/{friendId}")
    public ResponseEntity<String> acceptFriendRequest(@PathVariable int userId, @PathVariable int friendId) {
        if (authHelper.getCurrentUser().getId() != userId) {
            return ResponseEntity.status(403).body("You are not authorized to accept this friend request.");
        }

        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        Friends friends = friendshipService.acceptFriendRequest(user, friend);
        if (friends == null) {
            return ResponseEntity.status(400).body("Friend request not found or already accepted.");
        }

        return ResponseEntity.ok("Friendship accepted.");
    }

    @DeleteMapping("/remove/{userId}/{friendId}")
    public ResponseEntity<?> removeFriendship(@PathVariable int userId, @PathVariable int friendId) {
        int currentUserId = authHelper.getCurrentUser().getId();
        if (currentUserId != userId && currentUserId != friendId) {
            return ResponseEntity.status(403).body("You are not authorized to remove this friendship.");
        }

        User user = userService.findById(userId);
        User friend = userService.findById(friendId);
        friendshipService.removeFriendship(user, friend);
        return ResponseEntity.ok("Friendship removed.");
    }
    @PostMapping("/decline")
    public ResponseEntity<String> declineFriendRequest(@RequestBody FriendRequestDTO requestDTO) {
        if (authHelper.getCurrentUser().getId() != requestDTO.getReceiverId()) {
            return ResponseEntity.status(403).body("You are not authorized to decline this friend request.");
        }

        User sender = userService.findById(requestDTO.getSenderId());
        User receiver = userService.findById(requestDTO.getReceiverId());

        friendshipService.declineFriendRequest(receiver, sender);

        return ResponseEntity.ok("Friend request declined.");
    }
    @GetMapping("/status/{userId}/{friendId}")
    public ResponseEntity<FriendRequestDTO> checkStatus(@PathVariable int userId, @PathVariable int friendId) {
        int currentUserId = authHelper.getCurrentUser().getId();
        if (currentUserId != userId && currentUserId != friendId) {
            return ResponseEntity.status(403).build();
        }

        User user = userService.findById(userId);
        User friend = userService.findById(friendId);

        FriendRequestDTO relationshipStatus = friendshipService.getRelationshipBetweenUsers(user, friend);
        return ResponseEntity.ok(relationshipStatus);
    }

    // SUG-001: the authenticated user comes ONLY from authHelper.getCurrentUser() - no userId is
    // ever accepted from the client for this endpoint, unlike the other endpoints above (which
    // operate on a specific, caller-supplied friendship pair and therefore validate a path
    // userId against it). "Suggestions" are inherently "for whoever is asking," so there is
    // nothing to validate.
    // SUG-002: cursor/seed/wrapped together form one traversal's cursor state, entirely
    // round-tripped by the client (see SuggestedFriendsResponseDTO) - nothing is kept
    // server-side between requests. seed/wrapped are omitted on the very first call of a fresh
    // traversal; every subsequent "Show more" call for that same traversal must echo back
    // exactly what the previous response returned for all three.
    @GetMapping("/suggestions")
    public ResponseEntity<SuggestedFriendsResponseDTO> getSuggestedFriends(
            @RequestParam(required = false) Integer cursor,
            @RequestParam(required = false) Integer seed,
            @RequestParam(required = false) Boolean wrapped,
            @RequestParam(required = false) Integer limit) {
        User currentUser = authHelper.getCurrentUser();
        SuggestedFriendsResponseDTO response = friendshipService.getSuggestedFriends(currentUser, cursor, seed, wrapped, limit);
        return ResponseEntity.ok(response);
    }
}

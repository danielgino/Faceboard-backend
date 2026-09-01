package org.example.apimywebsite.api.controller;

import jakarta.validation.Valid;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/user")

public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;
    @Autowired
    private AuthHelper authHelper;

    @PostMapping("/{id}/profile-picture")
    public ResponseEntity<String> uploadProfilePicture(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        if (authHelper.getCurrentUser().getId() != id) {
            return ResponseEntity.status(403).body("You are not authorized to modify this profile picture.");
        }

        try {
            String imageUrl = userService.uploadProfilePicture(id, file);
            return ResponseEntity.ok("Profile picture updated: " + imageUrl);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Error uploading image");
        }
    }

    @GetMapping("/{id}/profile-picture")
    public ResponseEntity<String> getUserProfilePicture(@PathVariable int id) {
        User user = userService.getUser(id);
        if (user == null || user.getProfilePictureUrl() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No profile picture found");
        }
        return ResponseEntity.ok(user.getProfilePictureUrl());
    }
    @DeleteMapping("/{id}/profile-picture")
    public ResponseEntity<String> removeProfilePicture(@PathVariable int id) {
        if (authHelper.getCurrentUser().getId() != id) {
            return ResponseEntity.status(403).body("You are not authorized to modify this profile picture.");
        }

        userService.removeProfilePicture(id);
        return ResponseEntity.ok("Profile picture removed");
    }

    // M-OOP1: acting user now resolved via AuthHelper (already used elsewhere in this class)
    // instead of duplicating SecurityContextHolder access here.
    @PutMapping("/settings")
    public ResponseEntity<UserDTO> updateProfile(
            @Valid  @RequestBody UpdateUserDTO dto) {

        User user = authHelper.getCurrentUser();
        UserDTO updatedUser = userService.updateUserDetails(user.getId(), dto);
        return ResponseEntity.ok(updatedUser);
    }
    @GetMapping("/{userId}/friends")
    public ResponseEntity<UserFriendsDTO> getUserFriends(@PathVariable int userId) {
        UserFriendsDTO response = userService.getFriendsByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/friends/page")
    public ResponseEntity<UserFriendsDTO> getUserFriendsPage(
            @PathVariable int userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query) {
        UserFriendsDTO response = userService.getFriendsPageByUserId(userId, page, size, query);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterDTO dto) {
    log.info("Registration request received for username: {}", dto.getUsername());

    userService.register(dto);
    return ResponseEntity.ok("User registered successfully");
}
    // Public-profile finding: self-only now - no legitimate caller needs another user's raw/full
    // record through this endpoint (GET /user/by-id already serves the public-profile case).
    // Objects.equals (value comparison) rather than != : getCurrentUser().getId() is a primitive
    // int, but kept explicit/unambiguous rather than relying on int/Integer auto-unboxing.
    @GetMapping("/id")
    public ResponseEntity<?> getUser(@RequestParam Integer id) {
        if (!Objects.equals(authHelper.getCurrentUser().getId(), id)) {
            return ResponseEntity.status(403).body("You are not authorized to view this user.");
        }
        return ResponseEntity.ok(userService.getUserDTOById(id));
    }
    // Confirmed-dead-endpoint finding: GET /user/email and GET /user/username (arbitrary
    // exact-match account lookup, open to any authenticated user, zero frontend/backend/test
    // consumers) were removed - they allowed account lookup/enumeration with no legitimate use.
    // UserService.findByEmail/findByUserName are unaffected and remain in use internally
    // (register/login/updateUserDetails/AuthHelper/JwtAuthFilter/JwtChannelInterceptor/
    // WebSocketDisconnectListener/AuthController/CommentService/LikeService/StoryController).
    // Legitimate lookups remain served by /user/by-id (PublicUserProfileDTO) and /user/name
    // (UserSearchResultDTO search), both already scoped to public-safe fields.

    @GetMapping("/name")
    public ResponseEntity<List<UserSearchResultDTO>> getUsersByName(@RequestParam String name) {
        List<UserSearchResultDTO> results = userService.searchUsersByName(name);
        if (results.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(results);
    }

    // Public-profile finding: one uniform PublicUserProfileDTO shape for self and non-self alike
    // (no requester-equality branching) - no email, no message-preview data. getUserDTOById
    // (unchanged, full UserDTO) remains the self/account path, reached only via GET /auth/me.
    @GetMapping("/by-id")
    public ResponseEntity<PublicUserProfileDTO> getUserDTOById(@RequestParam int id) {
        return ResponseEntity.ok(userService.getPublicUserProfileById(id));
    }
}
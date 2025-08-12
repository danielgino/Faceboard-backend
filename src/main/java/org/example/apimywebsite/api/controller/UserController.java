package org.example.apimywebsite.api.controller;

import jakarta.validation.Valid;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.*;
import org.example.apimywebsite.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/user")

public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/{id}/profile-picture")
    public ResponseEntity<String> uploadProfilePicture(@PathVariable int id, @RequestParam("file") MultipartFile file) {
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
        userService.removeProfilePicture(id);
        return ResponseEntity.ok("Profile picture removed");
    }

    @PutMapping("/settings")
    public ResponseEntity<UserDTO> updateProfile(
            @Valid  @RequestBody UpdateUserDTO dto) {

        String username = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User user = userService.findByUserName(username);
        UserDTO updatedUser = userService.updateUserDetails(user.getId(), dto);
        return ResponseEntity.ok(updatedUser);
    }
    @GetMapping("/{userId}/friends")
    public ResponseEntity<UserFriendsDTO> getUserFriends(@PathVariable int userId) {
        UserFriendsDTO response = userService.getFriendsByUserId(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterDTO dto) {
    System.out.println("REGISTER REQUEST RECEIVED: " + dto.getUsername());

    userService.register(dto);
    return ResponseEntity.ok("User registered successfully");
}
    @GetMapping("/id")
    public ResponseEntity<User> getUser(@RequestParam Integer id) {
        User user = userService.getUser(id);
        if (user != null) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    @GetMapping("/email")
    public ResponseEntity<String> findByEmail(@RequestParam String email) {
        User user = userService.findByEmail(email);
        if (user != null) {
            return ResponseEntity.ok(user.getEmail());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/username")
    public ResponseEntity<String> findByUserName(@RequestParam String userName) {
        User user = userService.findByUserName(userName);
        if (user != null) {
            return ResponseEntity.ok(user.getUserName());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/name")
    public ResponseEntity<List<UserDTO>> getUsersByName(@RequestParam String name) {
        List<UserDTO> userDTOs = userService.findUsersByFullName(name);
        if (userDTOs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(userDTOs);
    }

    @GetMapping("/by-id")
    public ResponseEntity<UserDTO> getUserDTOById(@RequestParam int id) {
        UserDTO userDTO =userService.getUserDTOById(id);
       if (userDTO!=null){
           return ResponseEntity.ok(userDTO);
       }
       else {
           return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
       }

    }
}
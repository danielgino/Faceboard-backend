package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

// Public-profile finding: an explicit allowlist for a friend entry shown on another user's
// profile (via PublicUserProfileDTO.friendsList). Deliberately does not carry lastMessageContent,
// lastMessageTime, sentByCurrentUser, email, or birthDate - those belong only to the caller's own
// account view (AuthController.getUserDetails / UserService.getUserDTOById), never to a public
// profile lookup of someone else.
@Getter
@Setter
public class PublicFriendDTO {
    private int id;
    private String username;
    private String fullName;
    private String profilePictureUrl;
}

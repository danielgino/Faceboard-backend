package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.apimywebsite.enums.Gender;

import java.time.LocalDate;
import java.util.List;

// Public-profile finding: the response shape for GET /user/by-id - one uniform schema for both
// self and non-self lookups (no requester-dependent branching). Deliberately excludes email and
// any message-preview data; those remain available only via the caller's own account view
// (GET /auth/me, backed by the separate, unchanged UserDTO/getUserDTOById path).
@Getter
@Setter
public class PublicUserProfileDTO {
    private int id;
    private String username;
    private String name;
    private String fullName;
    private String profilePictureUrl;
    private String bio;
    private Gender gender;
    private LocalDate birthDate;
    private String facebookUrl;
    private String instagramUrl;
    private List<PublicFriendDTO> friendsList;
}

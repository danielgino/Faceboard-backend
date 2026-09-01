package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

// Public-profile finding: the exact allowlist consumed by the people-search feature
// (SearchProvider/Search.js/SearchPage.js in the frontend) - id, fullName, profilePictureUrl only.
// No email/birthDate/bio/social-link fields, unlike the UserDTO this endpoint used to return.
@Getter
@Setter
public class UserSearchResultDTO {
    private int id;
    private String fullName;
    private String profilePictureUrl;
}

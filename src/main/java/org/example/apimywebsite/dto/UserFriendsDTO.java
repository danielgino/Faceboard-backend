package org.example.apimywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserFriendsDTO {
    private int id;
    private String fullName;
    private String username;
    private String profilePictureUrl;
    private List<FriendDTO> friendList;
}

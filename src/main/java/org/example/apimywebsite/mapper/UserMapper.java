package org.example.apimywebsite.mapper;

import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendDTO;
import org.example.apimywebsite.dto.PublicFriendDTO;
import org.example.apimywebsite.dto.PublicUserProfileDTO;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.dto.UserSearchResultDTO;
import org.mapstruct.*;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "userName", target = "username")
    UserDTO toUserDTO(User user);
    default UserDTO toUserDTOWithFriends(User user, List<User> friends) {
        UserDTO dto = toUserDTO(user);
        List<FriendDTO> friendDTOs = friends.stream()
                .map(f -> new FriendDTO(f.getId(), f.getName(), f.getLastname(), f.getUserName(), f.getProfilePictureUrl()))
                .collect(Collectors.toList());
        dto.setFriendsList(friendDTOs);
        return dto;
    }

    default UserDTO toUserDTOWithFriendsAndLastMessage(User user, List<FriendDTO> enrichedFriends) {
        UserDTO dto = toUserDTO(user);
        dto.setFriendsList(enrichedFriends);
        return dto;
    }
    default FriendDTO toFriendDTO(User user) {
        return new FriendDTO(user.getId(), user.getName(), user.getLastname(), user.getUserName(), user.getProfilePictureUrl());
    }

    default FriendDTO toFriendDTOWithMessage(User friend, Message lastMessage, int currentUserId) {
        FriendDTO dto = toFriendDTO(friend);
        if (lastMessage != null) {
            dto.setLastMessageContent(lastMessage.getMessage());
            dto.setLastMessageTime(lastMessage.getSentTime());
            dto.setSentByCurrentUser(lastMessage.getSender().getId() == currentUserId);
        } else {
            dto.setLastMessageContent("No messages yet");
            dto.setLastMessageTime(null);
            dto.setSentByCurrentUser(false);
        }
        return dto;
    }

    // Public-profile finding: no message data anywhere in this path - unlike
    // toFriendDTOWithMessage above, this never touches Message/MessageRepository at all.
    default PublicFriendDTO toPublicFriendDTO(User friend) {
        PublicFriendDTO dto = new PublicFriendDTO();
        dto.setId(friend.getId());
        dto.setUsername(friend.getUserName());
        dto.setFullName(friend.getFullName());
        dto.setProfilePictureUrl(friend.getProfilePictureUrl());
        return dto;
    }

    default UserSearchResultDTO toUserSearchResultDTO(User user) {
        UserSearchResultDTO dto = new UserSearchResultDTO();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        return dto;
    }

    // One uniform shape for GET /user/by-id regardless of whether the requested id is the
    // caller's own - no email, no friendsList message previews. Full self/account data (with
    // message previews) remains available only via toUserDTOWithFriendsAndLastMessage (/auth/me).
    default PublicUserProfileDTO toPublicUserProfileDTO(User user, List<PublicFriendDTO> friends) {
        PublicUserProfileDTO dto = new PublicUserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUserName());
        dto.setName(user.getName());
        dto.setFullName(user.getFullName());
        dto.setProfilePictureUrl(user.getProfilePictureUrl());
        dto.setBio(user.getBio());
        dto.setGender(user.getGender());
        dto.setBirthDate(user.getBirthDate());
        dto.setFacebookUrl(user.getFacebookUrl());
        dto.setInstagramUrl(user.getInstagramUrl());
        dto.setFriendsList(friends);
        return dto;
    }
}

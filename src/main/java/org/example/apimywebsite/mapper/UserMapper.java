package org.example.apimywebsite.mapper;

import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendDTO;
import org.example.apimywebsite.dto.UserDTO;
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
}

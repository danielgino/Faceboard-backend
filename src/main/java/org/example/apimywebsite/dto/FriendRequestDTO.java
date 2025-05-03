package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FriendRequestDTO {

    private int senderId;
    private int receiverId;

    private String status;

    public FriendRequestDTO(int senderId, int receiverId,String status) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status=status;
    }

}

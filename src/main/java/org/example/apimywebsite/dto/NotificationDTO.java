package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class NotificationDTO {
    private Long id;
    private String type;
    private String content;
    private boolean read;
    private LocalDateTime createdAt;
    private Integer senderId;
    private String senderName;
    private String senderProfilePicture;
    private Long postId;

    public NotificationDTO() {}

    public NotificationDTO(Long id, String type, String content, boolean read, LocalDateTime createdAt,
                           Integer senderId, String senderName, String senderProfilePicture, Long postId) {
        this.id = id;
        this.type = type;
        this.content = content;
        this.read = read;
        this.createdAt = createdAt;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderProfilePicture = senderProfilePicture;
        this.postId = postId;
    }

}

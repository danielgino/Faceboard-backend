package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CommentDTO {
    private long commentId;
    private long userId;
    private String fullName;

    private String username;
    private String commentText;
    private LocalDateTime createdAt;


    private String profilePicture;


    public CommentDTO(long commentId, long userId, String fullName,String username ,String commentText, LocalDateTime createdAt,String profilePicture) {
        this.commentId = commentId;
        this.userId = userId;
        this.fullName = fullName;
        this.username=username;
        this.commentText = commentText;
        this.createdAt = createdAt;
        this.profilePicture = profilePicture;
    }
    public CommentDTO() {

    }

}

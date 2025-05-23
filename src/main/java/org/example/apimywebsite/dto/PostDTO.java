package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.apimywebsite.api.model.PostImage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Setter
@Getter
public class PostDTO {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private int userId;
    private String fullName;
    private String username;
    private int likeCount;
    private int commentCount;
    private String profilePictureUrl;
    private List<String> imageUrls;
    private boolean likedByCurrentUser;
//    private List<LikeDTO> likedUsers;
//    private List<CommentDTO> comments;

    private boolean edited=false;

    public PostDTO(long id, int userId,String username, String fullName, String content, LocalDateTime createdAt,
                   int likeCount,Boolean likedByCurrentUser
                   ,String profilePictureUrl,List<String> imageUrls,boolean edited) {

        this.id = id;
        this.userId = userId;
        this.username=username;
        this.fullName = fullName;
        this.content = content;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
//        this.likedUsers = likedUsers;
//        this.comments = comments;
        this.likedByCurrentUser=likedByCurrentUser;

        this.profilePictureUrl =profilePictureUrl;
        this.imageUrls=imageUrls;
        this.edited=edited;


    }


}

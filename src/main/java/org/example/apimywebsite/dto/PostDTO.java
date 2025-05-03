package org.example.apimywebsite.dto;

import org.example.apimywebsite.api.model.PostImage;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class PostDTO {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private int userId;
    private String fullName;
    private String username;
    private int likeCount;

    private List<LikeDTO> likedUsers;
    private String profilePictureUrl;

    private List<String> imageUrls;
    private boolean likedByCurrentUser;

    private List<CommentDTO> comments;

    private boolean edited=false;

    public PostDTO(long id, int userId,String username, String fullName, String content, LocalDateTime createdAt,
                   int likeCount, List<LikeDTO> likedUsers,Boolean likedByCurrentUser,
                   List<CommentDTO> comments,String profilePictureUrl,List<String> imageUrls,boolean edited) {

        this.id = id;
        this.userId = userId;
        this.username=username;
        this.fullName = fullName;
        this.content = content;
        this.createdAt = createdAt;
        this.likeCount = likeCount;
        this.likedUsers = likedUsers;
        this.likedByCurrentUser=likedByCurrentUser;
        this.comments = comments;
        this.profilePictureUrl =profilePictureUrl;
        this.imageUrls=imageUrls;
        this.edited=edited;


    }



    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public List<LikeDTO> getLikedUsers() {
        return likedUsers;
    }

    public void setLikedUsers(List<LikeDTO> likedUsers) {
        this.likedUsers = likedUsers;
    }

    public List<CommentDTO> getComments() {
        return comments;
    }

    public void setComments(List<CommentDTO> comments) {
        this.comments = comments;
    }



    public boolean isEdited() {
        return edited;
    }

    public void setEdited(boolean edited) {
        this.edited = edited;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String name) {
        this.fullName = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public void setProfilePictureUrl(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isLikedByCurrentUser() {
        return likedByCurrentUser;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        this.likedByCurrentUser = likedByCurrentUser;
    }


}

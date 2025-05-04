package org.example.apimywebsite.mapper;


import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "id", source = "post.postId")
    @Mapping(target = "userId", source = "post.user.id")
    @Mapping(target = "username", source = "post.user.userName")
    @Mapping(target = "fullName", expression = "java(post.getUser().getFullName())")
    @Mapping(target = "profilePictureUrl", source = "post.user.profilePictureUrl")
    @Mapping(target = "content", source = "post.postText")
    @Mapping(target = "createdAt", source = "post.createdAt")
    @Mapping(target = "likeCount", expression = "java(post.getLikes() != null ? post.getLikes().size() : 0)")
    @Mapping(target = "likedUsers", expression = "java(emptyLikeList())")
    @Mapping(target = "likedByCurrentUser", expression = "java(post.getLikes() != null && post.getLikes().stream().anyMatch(like -> like.getUser().getId() == currentUserId))")
    @Mapping(target = "comments", expression = "java(mapComments(post.getComments()))")
    @Mapping(target = "imageUrls", expression = "java(mapImageUrls(post))")
    @Mapping(target = "edited", source = "post.edited")
    PostDTO toDto(Post post, int currentUserId);

    default List<String> mapImageUrls(Post post) {
        if (post.getImages() == null) return null;
        return post.getImages().stream()
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }
    default List<LikeDTO> emptyLikeList() {
        return Collections.emptyList();
    }
    default List<CommentDTO> mapComments(List<Comment> comments) {
        if (comments == null) return null;
        return comments.stream().map(comment -> {
            CommentDTO dto = new CommentDTO();
            dto.setCommentId(comment.getCommentId());
            dto.setUserId(comment.getUser().getId());
            dto.setUsername(comment.getUser().getUserName());
            dto.setFullName(comment.getUser().getFullName());
            dto.setProfilePicture(comment.getUser().getProfilePictureUrl());
            dto.setCommentText(comment.getText());
            dto.setCreatedAt(comment.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }
}
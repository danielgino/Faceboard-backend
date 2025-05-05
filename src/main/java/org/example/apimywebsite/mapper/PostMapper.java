package org.example.apimywebsite.mapper;


import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collection;
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
//    @Mapping(target = "likeCount", expression = "java(post.getLikes() != null ? post.getLikes().size() : 0)")
//    @Mapping(target = "likedUsers", expression = "java(mapLikedUsers(post))")
    @Mapping(target = "likedByCurrentUser", expression = "java(post.getLikes() != null && post.getLikes().stream().anyMatch(like -> like.getUser().getId() == currentUserId))")
//    @Mapping(target = "comments", expression = "java(mapComments(post.getComments()))")
    @Mapping(target = "imageUrls", expression = "java(mapImageUrls(post))")
    @Mapping(target = "edited", source = "post.edited")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "commentCount", source = "commentCount")
    PostDTO toDto(Post post, int currentUserId, int likeCount, int commentCount);

    default List<String> mapImageUrls(Post post) {
        if (post.getImages() == null) return Collections.emptyList();
        return post.getImages().stream()
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }



}
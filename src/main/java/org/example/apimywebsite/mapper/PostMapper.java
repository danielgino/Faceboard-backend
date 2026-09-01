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

    // DBP-003 fix: likedByCurrentUser used to be computed via post.getLikes().stream().anyMatch(...),
    // which initialized the post's *entire* Like collection just to answer one boolean - a
    // popular post's whole like history loaded merely to check whether one user is in it. The
    // caller now determines this via a narrow, bounded query (see PostService) and passes the
    // already-computed result straight through; Post.likes is never touched here.
    @Mapping(target = "id", source = "post.postId")
    @Mapping(target = "userId", source = "post.user.id")
    @Mapping(target = "username", source = "post.user.userName")
    @Mapping(target = "fullName", expression = "java(post.getUser().getFullName())")
    @Mapping(target = "profilePictureUrl", source = "post.user.profilePictureUrl")
    @Mapping(target = "content", source = "post.postText")
    @Mapping(target = "createdAt", source = "post.createdAt")
    @Mapping(target = "likedByCurrentUser", source = "likedByCurrentUser")
    @Mapping(target = "imageUrls", expression = "java(mapImageUrls(post))")
    @Mapping(target = "edited", source = "post.edited")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "commentCount", source = "commentCount")
    PostDTO toDto(Post post, boolean likedByCurrentUser, int likeCount, int commentCount);

    default List<String> mapImageUrls(Post post) {
        if (post.getImages() == null) return Collections.emptyList();
        return post.getImages().stream()
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }



}
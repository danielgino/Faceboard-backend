package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.*;

import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.dto.PostImageDTO;
import org.example.apimywebsite.mapper.PostMapper;
import org.example.apimywebsite.repository.*;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PostImageRepository postImageRepository;
    private final AuthHelper authHelper;
    private final NotificationRepository notificationRepository;
    private final PostMapper postMapper;

    @Autowired
    public PostService(PostRepository postRepository,JwtUtil jwtUtil,UserRepository userRepository,SimpMessagingTemplate messagingTemplate,PostImageRepository postImageRepository,AuthHelper authHelper,NotificationRepository notificationRepository,PostMapper postMapper) {
        this.postRepository = postRepository;
        this.jwtUtil=jwtUtil;
        this.userRepository=userRepository;
        this.messagingTemplate=messagingTemplate;
        this.postImageRepository=postImageRepository;
        this.authHelper=authHelper;
        this.notificationRepository=notificationRepository;
        this.postMapper=postMapper;
    }


//    public PostDTO addPost(Post post, String authHeader,List<String> imageUrls) {
//        String token = authHeader.substring(7);
//        String username = jwtUtil.extractUsername(token);
//        User user = userRepository.findByUserName(username);
//        if (user == null) {
//            throw new RuntimeException("User not found");
//        }
//        post.setUser(user);
//        post.setCreatedAt(LocalDateTime.now());
//        postRepository.save(post);
//
//        List<PostImage> images = new ArrayList<>();
//        if (imageUrls != null && !imageUrls.isEmpty()) {
//            for (String url : imageUrls) {
//                PostImage image = new PostImage();
//                image.setPost(post);
//                image.setImageUrl(url);
//                postImageRepository.save(image);
//                images.add(image);
//            }
//        }
//
//        post.getImages().addAll(images);
//
//        postRepository.save(post);
//        int likeCount = post.getLikes() != null ? post.getLikes().size() : 0;
//        String fullName = post.getUser().getFullName();
//
//        List<LikeDTO> likedUsers = new ArrayList<>();
//        if (post.getLikes() != null) {
//            for (Like like : post.getLikes()) {
//                User likedUser = like.getUser();
//                likedUsers.add(new LikeDTO(
//                        likedUser.getId(),
//                        likedUser.getFullName(),
//                        likedUser.getUserName(),
//                        likedUser.getProfilePictureUrl()
//                ));
//            }
//        }
//        List<CommentDTO> comments = new ArrayList<>();
//        if (post.getComments() != null) {
//            for (Comment comment : post.getComments()) {
//                CommentDTO commentDTO = new CommentDTO(
//                        comment.getCommentId(),
//                        comment.getUser().getId(),
//                        comment.getUser().getFullName(),
//                        comment.getUser().getUserName(),
//                        comment.getText(),
//                        comment.getCreatedAt(),
//                        comment.getUser().getProfilePictureUrl()
//                );
//                comments.add(commentDTO);
//            }
//        }
//
//        PostDTO postDTO = new PostDTO(
//                post.getPostId(),
//                post.getUser().getId(),
//                post.getUser().getUserName(),
//                fullName,
//                post.getPostText(),
//                post.getCreatedAt(),
//                likeCount,
//                likedUsers,
//                false,
//                comments,
//                post.getUser().getProfilePictureUrl(),
//                imageUrls,
//                false
//
//
//        );
//
//        messagingTemplate.convertAndSend("/topic/posts", postDTO);
//        return postDTO;
//    }
public PostDTO addPost(Post post, List<String> imageUrls) {
    User user = authHelper.getCurrentUser();
    post.setUser(user);
    post.setCreatedAt(LocalDateTime.now());
    postRepository.save(post);
    List<PostImage> images = imageUrls.stream()
            .map(url -> {
                PostImage image = new PostImage();
                image.setPost(post);
                image.setImageUrl(url);
                return image;
            })
            .toList();
    postImageRepository.saveAll(images);
    post.setImages(images);
    PostDTO postDTO = postMapper.toDto(post, user.getId());
    messagingTemplate.convertAndSend("/topic/posts", postDTO);
    return postDTO;
}

    public List<PostDTO> getFeedPosts( int page, int size) {
        User currentUser = authHelper.getCurrentUser();
        List<Integer> userIds = new ArrayList<>();
        userIds.add(currentUser.getId());
        if (currentUser.getFriends() != null) {
            userIds.addAll(
                    currentUser.getFriends().stream()
                            .map(User::getId)
                            .toList()
            );
        }
        Pageable pageable = PageRequest.of(page, size);
        List<Post> posts = postRepository.findAllPostsWithImages(userIds, pageable);
        return posts.stream()
                .map(post -> postMapper.toDto(post, currentUser.getId()))
                .toList();
    }



    public List<String> getAllPostImageUrlsByUserId(int userId) {
        User currentUser = authHelper.getCurrentUser();
        List<Post> posts = getPostsByUserId(userId);
        return posts.stream()
                .flatMap(post -> post.getImages().stream())
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }
    public List<PostDTO> getPostsByUserDTO(long userId, int page, int size) {
        User currentUser = authHelper.getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        List<Post> posts = postRepository.findAllPostsWithImagesByUserId(userId, pageable);
        return posts.stream()
                .map(post -> postMapper.toDto(post, currentUser.getId()))
                .toList();
    }


    @Transactional
    public PostDTO editPost(long postId, String newContent) {
        User currentUser = authHelper.getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (post.getUser().getId() != currentUser.getId()) {
            throw new RuntimeException("You are not authorized to edit this post");
        }

        post.setPostText(newContent);
        post.setEdited(true);
        postRepository.save(post);

        return postMapper.toDto(post, currentUser.getId());
    }

    public List<Post> getPostsByUserId(long userId) {
        return postRepository.findByUserId(userId);
    }


    @Transactional
    public void deletePost(long postId) {
        User currentUser = authHelper.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        if (post.getUser().getId() != currentUser.getId()) {
            throw new RuntimeException("You are not authorized to delete this post");
        }
        notificationRepository.deleteByPost(post);
        postImageRepository.deleteAll(post.getImages());

        postRepository.delete(post);
    }

}


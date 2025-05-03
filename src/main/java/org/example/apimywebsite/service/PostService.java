package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.*;

import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.dto.PostImageDTO;
import org.example.apimywebsite.repository.NotificationRepository;
import org.example.apimywebsite.repository.PostImageRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    //@Autowired
    private final PostImageRepository postImageRepository;
    private final AuthHelper authHelper;
    private final NotificationRepository notificationRepository;
    @Autowired
    public PostService(PostRepository postRepository,JwtUtil jwtUtil,UserRepository userRepository,SimpMessagingTemplate messagingTemplate,PostImageRepository postImageRepository,AuthHelper authHelper,NotificationRepository notificationRepository) {
        this.postRepository = postRepository;
        this.jwtUtil=jwtUtil;
        this.userRepository=userRepository;
        this.messagingTemplate=messagingTemplate;
        this.postImageRepository=postImageRepository;
        this.authHelper=authHelper;
        this.notificationRepository=notificationRepository;
    }


    public PostDTO addPost(Post post, String authHeader,List<String> imageUrls) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUserName(username);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        post.setUser(user);
        post.setCreatedAt(LocalDateTime.now());
        postRepository.save(post);

        List<PostImage> images = new ArrayList<>();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            for (String url : imageUrls) {
                PostImage image = new PostImage();
                image.setPost(post);
                image.setImageUrl(url);
                postImageRepository.save(image);
                images.add(image);
            }
        }

        post.getImages().addAll(images);

        postRepository.save(post);
        int likeCount = post.getLikes() != null ? post.getLikes().size() : 0;
        String fullName = post.getUser().getFullName();

        List<LikeDTO> likedUsers = new ArrayList<>();
        if (post.getLikes() != null) {
            for (Like like : post.getLikes()) {
                User likedUser = like.getUser();
                likedUsers.add(new LikeDTO(
                        likedUser.getId(),
                        likedUser.getFullName(),
                        likedUser.getUserName(),
                        likedUser.getProfilePictureUrl()
                ));
            }
        }
        List<CommentDTO> comments = new ArrayList<>();
        if (post.getComments() != null) {
            for (Comment comment : post.getComments()) {
                CommentDTO commentDTO = new CommentDTO(
                        comment.getCommentId(),
                        comment.getUser().getId(),
                        comment.getUser().getFullName(),
                        comment.getUser().getUserName(),
                        comment.getText(),
                        comment.getCreatedAt(),
                        comment.getUser().getProfilePictureUrl()
                );
                comments.add(commentDTO);
            }
        }

        PostDTO postDTO = new PostDTO(
                post.getPostId(),
                post.getUser().getId(),
                post.getUser().getUserName(),
                fullName,
                post.getPostText(),
                post.getCreatedAt(),
                likeCount,
                likedUsers,
                false,
                comments,
                post.getUser().getProfilePictureUrl(),
                imageUrls,
                false


        );

        messagingTemplate.convertAndSend("/topic/posts", postDTO);
        return postDTO;
    }

//

    public List<PostDTO> getFeedPosts(String authHeader, int page, int size) {
        User currentUser = authHelper.getUserFromAuthHeader(authHeader);
        List<PostDTO> postDTOs = new ArrayList<>();

        List<User> allUsersToInclude = new ArrayList<>();
        allUsersToInclude.add(currentUser);
        if (currentUser.getFriends() != null) {
            allUsersToInclude.addAll(currentUser.getFriends());
        }

        for (User user : allUsersToInclude) {
            List<Post> posts = getPostsByUserId(user.getId());

            for (Post post : posts) {
                int likeCount = post.getLikes() != null ? post.getLikes().size() : 0;

                List<LikeDTO> likedUsers = new ArrayList<>();
                if (post.getLikes() != null) {
                    for (Like like : post.getLikes()) {
                        User likedUser = like.getUser();
                        likedUsers.add(new LikeDTO(
                                likedUser.getId(),
                                likedUser.getFullName(),
                                likedUser.getUserName(),
                                likedUser.getProfilePictureUrl()
                        ));
                    }
                }

                List<CommentDTO> comments = new ArrayList<>();
                if (post.getComments() != null) {
                    for (Comment comment : post.getComments()) {
                        comments.add(new CommentDTO(
                                comment.getCommentId(),
                                comment.getUser().getId(),
                                comment.getUser().getFullName(),
                                comment.getUser().getUserName(),
                                comment.getText(),
                                comment.getCreatedAt(),
                                comment.getUser().getProfilePictureUrl()
                        ));
                    }
                }

                List<String> imageUrls = new ArrayList<>();
                if (post.getImages() != null) {
                    for (PostImage image : post.getImages()) {
                        imageUrls.add(image.getImageUrl());
                    }
                }

                boolean likedByCurrentUser = post.getLikes().stream()
                        .anyMatch(like -> like.getUser().getId() == currentUser.getId());

                postDTOs.add(new PostDTO(
                        post.getPostId(),
                        post.getUser().getId(),
                        post.getUser().getUserName(),
                        post.getUser().getFullName(),
                        post.getPostText(),
                        post.getCreatedAt(),
                        likeCount,
                        likedUsers,
                        likedByCurrentUser,
                        comments,
                        post.getUser().getProfilePictureUrl(),
                        imageUrls,
                        post.isEdited()
                ));
            }
        }

        postDTOs.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, postDTOs.size());

        if (fromIndex >= postDTOs.size()) {
            return new ArrayList<>();
        }

        return postDTOs.subList(fromIndex, toIndex);
    }

    public List<String> getAllPostImageUrlsByUserId(int userId,String authHeader) {
        User currentUser = authHelper.getUserFromAuthHeader(authHeader);
        if (currentUser == null) {
            throw new RuntimeException("Unauthorized: Please log in.");
        }

        List<Post> posts = getPostsByUserId(userId);

        return posts.stream()
                .flatMap(post -> post.getImages().stream())
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }

    public List<PostDTO> getPostsByUserDTO(long userId, String authHeader, int page, int size) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        User currentUser = userRepository.findByUserName(username);

        List<Post> posts = getPostsByUserId(userId);
        posts.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, posts.size());

        if (fromIndex >= posts.size()) {
            return new ArrayList<>();
        }

        List<Post> paginatedPosts = posts.subList(fromIndex, toIndex);

        List<PostDTO> postDTOs = new ArrayList<>();

        for (Post post : paginatedPosts) {
            int likeCount = post.getLikes() != null ? post.getLikes().size() : 0;

            List<LikeDTO> likedUsers = new ArrayList<>();
            if (post.getLikes() != null) {
                for (Like like : post.getLikes()) {
                    User likedUser = like.getUser();
                    likedUsers.add(new LikeDTO(
                            likedUser.getId(),
                            likedUser.getFullName(),
                            likedUser.getUserName(),
                            likedUser.getProfilePictureUrl()
                    ));
                }
            }

            List<CommentDTO> comments = new ArrayList<>();
            if (post.getComments() != null) {
                for (Comment comment : post.getComments()) {
                    comments.add(new CommentDTO(
                            comment.getCommentId(),
                            comment.getUser().getId(),
                            comment.getUser().getFullName(),
                            comment.getUser().getUserName(),
                            comment.getText(),
                            comment.getCreatedAt(),
                            comment.getUser().getProfilePictureUrl()
                    ));
                }
            }

            List<String> imageUrls = new ArrayList<>();
            if (post.getImages() != null) {
                for (PostImage image : post.getImages()) {
                    imageUrls.add(image.getImageUrl());
                }
            }

            boolean likedByCurrentUser = post.getLikes().stream()
                    .anyMatch(like -> like.getUser().getId() == currentUser.getId());

            postDTOs.add(new PostDTO(
                    post.getPostId(),
                    post.getUser().getId(),
                    post.getUser().getUserName(),
                    post.getUser().getFullName(),
                    post.getPostText(),
                    post.getCreatedAt(),
                    likeCount,
                    likedUsers,
                    likedByCurrentUser,
                    comments,
                    post.getUser().getProfilePictureUrl(),
                    imageUrls,
                    post.isEdited()
            ));
        }

        return postDTOs;
    }
    @Transactional
    public PostDTO editPost(long postId, String newContent, String authHeader) {
        User currentUser = authHelper.getUserFromAuthHeader(authHeader);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (post.getUser().getId() != currentUser.getId()) {
            throw new RuntimeException("You are not authorized to edit this post");
        }
        post.setPostText(newContent);
        post.setEdited(true);
        postRepository.save(post);

        int likeCount = post.getLikes() != null ? post.getLikes().size() : 0;

        List<LikeDTO> likedUsers = post.getLikes().stream()
                .map(like -> new LikeDTO(
                        like.getUser().getId(),
                        like.getUser().getFullName(),
                        like.getUser().getUserName(),
                        like.getUser().getProfilePictureUrl()
                ))
                .collect(Collectors.toList());

        List<CommentDTO> comments = post.getComments().stream()
                .map(comment -> new CommentDTO(
                        comment.getCommentId(),
                        comment.getUser().getId(),
                        comment.getUser().getFullName(),
                        comment.getUser().getUserName(),
                        comment.getText(),
                        comment.getCreatedAt(),
                        comment.getUser().getProfilePictureUrl()
                ))
                .collect(Collectors.toList());

        List<String> imageUrls = post.getImages().stream()
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());

        boolean likedByCurrentUser = post.getLikes().stream()
                .anyMatch(like -> like.getUser().getId() == currentUser.getId());

        return new PostDTO(
                post.getPostId(),
                post.getUser().getId(),
                post.getUser().getUserName(),
                post.getUser().getFullName(),
                post.getPostText(),
                post.getCreatedAt(),
                likeCount,
                likedUsers,
                likedByCurrentUser,
                comments,
                post.getUser().getProfilePictureUrl(),
                imageUrls,
                true
        );
    }

    public List<Post> getPostsByUserId(long userId) {
        return postRepository.findByUserId(userId);
    }


    @Transactional
    public void deletePost(long postId, String authHeader) {
        User currentUser = authHelper.getUserFromAuthHeader(authHeader);
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


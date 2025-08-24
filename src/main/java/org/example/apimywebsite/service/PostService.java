package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.*;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.mapper.PostMapper;
import org.example.apimywebsite.repository.*;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final  CommentRepository commentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PostImageRepository postImageRepository;
    private final AuthHelper authHelper;
    private final NotificationRepository notificationRepository;
    private final PostMapper postMapper;

    @Autowired
    public PostService(PostRepository postRepository,LikeRepository likeRepository,CommentRepository commentRepository,SimpMessagingTemplate messagingTemplate,PostImageRepository postImageRepository,AuthHelper authHelper,NotificationRepository notificationRepository,PostMapper postMapper) {
        this.postRepository = postRepository;
        this.likeRepository=likeRepository;
        this.commentRepository=commentRepository;
        this.messagingTemplate=messagingTemplate;
        this.postImageRepository=postImageRepository;
        this.authHelper=authHelper;
        this.notificationRepository=notificationRepository;
        this.postMapper=postMapper;
    }

public PostDTO addPost(Post post, List<String> imageUrls) {
    User user = authHelper.getCurrentUser();
    post.setUser(user);
    post.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
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
    post.setImages(new HashSet<>(images));
    PostDTO postDTO = postMapper.toDto(post, user.getId(), 0, 0);
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
                .map(post -> {
                    int likeCount = likeRepository.countLikesByPostId(post.getPostId());
                    int commentCount = commentRepository.countCommentsByPostId(post.getPostId());
                    return postMapper.toDto(post, currentUser.getId(), likeCount, commentCount);
                })
                .toList();

    }



    public List<String> getAllPostImageUrlsByUserId(int userId) {
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
                .map(post -> {
                    int likeCount = likeRepository.countLikesByPostId(post.getPostId());
                    int commentCount = commentRepository.countCommentsByPostId(post.getPostId());
                    return postMapper.toDto(post, currentUser.getId(), likeCount, commentCount);
                })
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
        int likeCount = 0;
        int commentCount = 0;
        return postMapper.toDto(post, currentUser.getId(), likeCount, commentCount);

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
    @Transactional
    public PostDTO getPostById(long postId) {
        User currentUser = authHelper.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        int likeCount = likeRepository.countLikesByPostId(post.getPostId());
        int commentCount = commentRepository.countCommentsByPostId(post.getPostId());

        return postMapper.toDto(post, currentUser.getId(), likeCount, commentCount);
    }
}


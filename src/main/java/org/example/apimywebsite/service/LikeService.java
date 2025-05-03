package org.example.apimywebsite.service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.repository.LikeRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService;
    private final PostRepository postRepository;

    public LikeService(LikeRepository likeRepository,PostRepository postRepository,JwtUtil jwtUtil,UserRepository userRepository,NotificationService notificationService){
        this.likeRepository=likeRepository;
        this.postRepository=postRepository;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.notificationService=notificationService;
    }
    public long getLikeCountForPost(Post post) {
        return likeRepository.countByPost(post);
    }
    @Transactional
    public void addLike(User user, Post post){
        Like like=new Like();
        like.setUser(user);
        like.setPost(post);
        like.setCreatedAt(LocalDateTime.now());
        likeRepository.save(like);

    }

@Transactional
public String addLike(Like like, HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new IllegalArgumentException("Missing or invalid Authorization header");
    }

    String token = authHeader.substring(7);
    String username = jwtUtil.extractUsername(token);
    User user = userRepository.findByUserName(username);
    if (user == null) {
        throw new IllegalArgumentException("User Not Found");
    }

    Long postId = like.getPost().getPostId();
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));

    if (likeRepository.existsByPost_PostIdAndUser_Id(postId, user.getId())) {
        removeLike(user, post);
        return "Like Removed";
    } else {
        addLike(user, post);
        User postOwner = post.getUser();
        if (postOwner != null && user.getId() != postOwner.getId()) {
            String content = user.getFullName() + " Liked Your Post";
            notificationService.createNotification(postOwner, user, "LIKE", content, post);
        }

        return "Like added !";
    }
}

    @Transactional
    public void removeLike(User user, Post post) {
        likeRepository.deleteByPost_PostIdAndUser_Id(post.getPostId(), user.getId());
    }

}





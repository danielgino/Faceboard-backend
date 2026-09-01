package org.example.apimywebsite.service;
import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.repository.LikeRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LikeService {
    private final LikeRepository likeRepository;
    private final AuthHelper authHelper;
    private final NotificationService notificationService;
    private final PostRepository postRepository;

    // L-DB4C: default/max page size for the bounded liker-list read below - mirrors the
    // Notification pagination precedent (L-DB4B).
    public static final int DEFAULT_LIKES_PAGE_SIZE = 50;
    public static final int MAX_LIKES_PAGE_SIZE = 100;

    public LikeService(LikeRepository likeRepository,PostRepository postRepository,AuthHelper authHelper,NotificationService notificationService){
        this.likeRepository=likeRepository;
        this.postRepository=postRepository;
        this.authHelper = authHelper;
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
    // L-DB4C: bounded, page-based read instead of the previous unbounded fetch. Newest likers
    // first (matches the repository's DESC ordering); older likers remain retrievable via
    // page=1, page=2, etc. This is the "Users Who Liked This Post" list only - it does not
    // affect M-DB1's grouped like-count queries or the feed's like-count/current-user-like logic.
    public List<LikeDTO> getUserLikesByPostId(Long postId, Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0)
                ? DEFAULT_LIKES_PAGE_SIZE
                : Math.min(size, MAX_LIKES_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        List<Like> likes = likeRepository.findByPostIdWithUser(postId, pageable);

        return likes.stream().map(like -> {
            LikeDTO dto = new LikeDTO();
            dto.setUserId(like.getUser().getId());
            dto.setUsername(like.getUser().getUserName());
            dto.setFullName(like.getUser().getFullName());
            dto.setProfilePictureUrl(like.getUser().getProfilePictureUrl());
            return dto;
        }).toList();
    }

// M-OOP1: previously re-parsed the Authorization header/JWT manually here (Pattern C) even
// though JwtAuthFilter already validates the token and populates SecurityContextHolder before
// this code runs - /likes/add-like is behind .anyRequest().authenticated(), so by the time this
// method executes, authentication is already guaranteed present. Now resolves the acting user
// via the canonical AuthHelper, matching every other authenticated business service.
@Transactional
public String addLike(Like like) {
    User user = authHelper.getCurrentUser();

    Long postId = like.getPost().getPostId();
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

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





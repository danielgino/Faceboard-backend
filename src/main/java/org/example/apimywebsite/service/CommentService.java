package org.example.apimywebsite.service;

import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class CommentService {
    private  CommentRepository commentRepository;
    private UserRepository userRepository;
    private JwtUtil jwtUtil;
    @Autowired
    private PostRepository postRepository;

    private NotificationService notificationService;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository, JwtUtil jwtUtil,PostRepository postRepository,NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.postRepository=postRepository;
        this.notificationService=notificationService;
    }

public CommentDTO addComment(Comment comment) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    String username = authentication.getName();
    User user = userRepository.findByUserName(username);
    if (user == null || comment.getPost() == null) {
        throw new IllegalArgumentException("User or Post not found");
    }
    Long postId = comment.getPost().getPostId();
    Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    comment.setUser(user);
    comment.setCreatedAt(LocalDateTime.now());
    Comment savedComment = commentRepository.save(comment);

    User postOwner = post.getUser();
    if (postOwner != null && user.getId()!=(postOwner.getId())) {
        String content = user.getFullName() + " Comment on your post";
        notificationService.createNotification(postOwner, user, "COMMENT", content, post);
    }
    return new CommentDTO(
            savedComment.getCommentId(),
            savedComment.getUser().getId(),
            savedComment.getUser().getFullName(),
            comment.getUser().getUserName(),
            savedComment.getText(),
            savedComment.getCreatedAt(),
            savedComment.getUser().getProfilePictureUrl()
    );
}

    public void deleteComment(Long commentId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User currentUser = userRepository.findByUserName(username);
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found or unauthorized");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        boolean isCommentOwner = comment.getUser().getId() == currentUser.getId();
        boolean isPostOwner = comment.getPost().getUser().getId() == currentUser.getId();
        if (!isCommentOwner && !isPostOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this comment");
        }

        commentRepository.delete(comment);
    }

}

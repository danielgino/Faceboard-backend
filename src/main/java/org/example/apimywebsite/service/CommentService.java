package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.AddCommentRequestDTO;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Service
public class CommentService {
    private
    CommentRepository commentRepository;
    private AuthHelper authHelper;

    @Autowired
    private PostRepository postRepository;

    private NotificationService notificationService;

    // L-DB4C: default/max page size for the bounded comment read below - mirrors the
    // Notification/User-search pagination precedent (M-DB2/L-DB4A/L-DB4B).
    public static final int DEFAULT_COMMENTS_PAGE_SIZE = 20;
    public static final int MAX_COMMENTS_PAGE_SIZE = 50;

    public CommentService(CommentRepository commentRepository, AuthHelper authHelper,PostRepository postRepository,NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.authHelper = authHelper;
        this.postRepository=postRepository;
        this.notificationService=notificationService;
    }

    // L-DB4C: bounded, page-based read instead of the previous unbounded fetch. The repository
    // query returns newest-first (DESC) so page 0 is the most recent N comments without missing
    // any; that page is reversed here to ASC before mapping, so the external chronological
    // contract (oldest -> newest) is unchanged for the default (no-params) call. Older comments
    // remain retrievable via page=1, page=2, etc., each individually returned in chronological
    // order for the frontend to prepend.
    public List<CommentDTO> getCommentsByPostId(Long postId, Integer page, Integer size) {
        int safePage = (page == null || page < 0) ? 0 : page;
        int safeSize = (size == null || size <= 0)
                ? DEFAULT_COMMENTS_PAGE_SIZE
                : Math.min(size, MAX_COMMENTS_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        List<Comment> comments = commentRepository.findCommentsByPostIdWithUser(postId, pageable);
        Collections.reverse(comments);

        return comments.stream().map(comment -> new CommentDTO(
                comment.getCommentId(),
                comment.getUser().getId(),
                comment.getUser().getFullName(),
                comment.getUser().getUserName(),
                comment.getText(),
                comment.getCreatedAt(),
                comment.getUser().getProfilePictureUrl()
        )).toList();
    }


    // Comment-hijacking finding: request is the allowlisted AddCommentRequestDTO (postId, text
    // only) - never a client-deserialized Comment. A brand-new Comment is always constructed
    // here, so its generated commentId is 0 (new-entity state) going into save(), which makes
    // Spring Data JPA always INSERT rather than merge-over an existing row.
    // COR-002 fix: the comment row and its notification were two separate, individually
    // auto-committing writes. If the notification save failed after the comment had already
    // committed, the client received an error and could safely retry - creating a second,
    // duplicate comment for what the server considered a fully-succeeded first attempt.
    // @Transactional makes the whole use case atomic: either both persist, or the comment save
    // rolls back too, so an error response accurately means "nothing happened" and a retry is
    // safe. (The notification's own WebSocket broadcast already defers itself to after-commit -
    // see NotificationService.createNotification - so no separate change is needed here for that.)
    @Transactional
    public CommentDTO addComment(AddCommentRequestDTO request) {
        // M-OOP1: acting user now resolved via the canonical AuthHelper instead of duplicating
        // SecurityContextHolder/findByUserName here; the explicit null-check (and its exact
        // 404 status/message) is preserved unchanged, since AuthHelper itself does not
        // null-check the repository lookup it performs.
        User user = authHelper.getCurrentUser();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setPost(post);
        comment.setText(request.getText());
        comment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        Comment savedComment = commentRepository.save(comment);

        User postOwner = post.getUser();
        if (postOwner != null && user.getId() != (postOwner.getId())) {
            String content = user.getFullName() + " Comment on your post";
            notificationService.createNotification(postOwner, user, "COMMENT", content, post);
        }
        return new CommentDTO(
                savedComment.getCommentId(),
                savedComment.getUser().getId(),
                savedComment.getUser().getFullName(),
                savedComment.getUser().getUserName(),
                savedComment.getText(),
                savedComment.getCreatedAt(),
                savedComment.getUser().getProfilePictureUrl()
        );
    }

    public void deleteComment(Long commentId) {
        // M-OOP1: same migration as addComment above - AuthHelper replaces the duplicated
        // SecurityContextHolder/findByUserName lookup; the existing null-check and its exact
        // 401 status/message are preserved unchanged.
        User currentUser = authHelper.getCurrentUser();
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

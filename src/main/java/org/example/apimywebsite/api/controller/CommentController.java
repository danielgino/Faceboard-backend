package org.example.apimywebsite.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.CommentService;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController {
    private CommentService commentService;

    @Autowired
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentDTO>> getCommentsForPost(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId));
    }

@PostMapping("/add-comment")
public ResponseEntity<CommentDTO> addComment(@RequestBody Comment comment) {
    try {
        CommentDTO savedCommentDTO = commentService.addComment(comment);
        return ResponseEntity.ok(savedCommentDTO);
    } catch (IllegalArgumentException e) {
        return ResponseEntity.status(404).build();
    } catch (Exception e) {
        return ResponseEntity.status(500).build();
    }
}
    @DeleteMapping("/delete/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }

    }
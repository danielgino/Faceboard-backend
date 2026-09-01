package org.example.apimywebsite.api.controller;

import jakarta.validation.Valid;
import org.example.apimywebsite.dto.AddCommentRequestDTO;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController {
    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    private CommentService commentService;

    @Autowired
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }
    // L-DB4C: page/size are optional so the existing frontend (which calls this with neither)
    // keeps working unchanged, now bounded to the newest 20 by default; older comments remain
    // reachable via ?page=1, ?page=2, etc. Response stays a flat List<CommentDTO>.
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentDTO>> getCommentsForPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(commentService.getCommentsByPostId(postId, page, size));
    }

@PostMapping("/add-comment")
public ResponseEntity<CommentDTO> addComment(@Valid @RequestBody AddCommentRequestDTO request) {
    try {
        CommentDTO savedCommentDTO = commentService.addComment(request);
        return ResponseEntity.ok(savedCommentDTO);
    } catch (ResponseStatusException e) {
        // H8b: CommentService now throws a typed ResponseStatusException (404) for these
        // cases instead of IllegalArgumentException; let it propagate to
        // GlobalExceptionHandler rather than swallowing it into the fallback below.
        throw e;
    } catch (Exception e) {
        log.error("Failed to add comment", e);
        return ResponseEntity.status(500).build();
    }
}
    @DeleteMapping("/delete/{commentId}")
    public ResponseEntity<String> deleteComment(@PathVariable Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }

    }
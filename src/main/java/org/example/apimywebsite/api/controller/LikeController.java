package org.example.apimywebsite.api.controller;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.service.LikeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/likes")
public class LikeController {
    private static final Logger log = LoggerFactory.getLogger(LikeController.class);

    private LikeService likeService;
    public LikeController(LikeService likeService) {
        this.likeService=likeService;
    }

    @PostMapping("/add-like")
    public ResponseEntity<String> addLike(@RequestBody Like like) {
        try {
            String resultMessage = likeService.addLike(like);
            return ResponseEntity.ok(resultMessage);
        } catch (ResponseStatusException e) {
            // H8b: LikeService now throws a typed, correctly-statused ResponseStatusException
            // (401/404) for these cases instead of IllegalArgumentException; let it propagate
            // to GlobalExceptionHandler rather than swallowing it into the fallback below.
            throw e;
        } catch (Exception e) {
            log.error("Failed to add like", e);
            return ResponseEntity.status(500).body(" Something went wrong like error");
        }
    }

    // L-DB4C: page/size are optional so the existing frontend (which calls this with neither)
    // keeps working unchanged, now bounded to the newest 50 by default; older likers remain
    // reachable via ?page=1, ?page=2, etc. Response stays a flat List<LikeDTO>.
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<LikeDTO>> getLikesForPost(
            @PathVariable Long postId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(likeService.getUserLikesByPostId(postId, page, size));
    }


}

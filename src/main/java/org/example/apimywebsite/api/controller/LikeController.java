package org.example.apimywebsite.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.repository.LikeRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.LikeService;
import org.example.apimywebsite.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/likes")
public class LikeController {


    private LikeService likeService;


    public LikeController(LikeService likeService) {
        this.likeService=likeService;
    }

    @PostMapping("/add-like")
    public ResponseEntity<String> addLike(@RequestBody Like like, HttpServletRequest request) {
        try {
            String resultMessage = likeService.addLike(like, request);
            return ResponseEntity.ok(resultMessage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(" Something went wrong like error");
        }
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<List<LikeDTO>> getLikesForPost(@PathVariable Long postId) {
        return ResponseEntity.ok(likeService.getUserLikesByPostId(postId));
    }


}

package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.dto.EditPostRequestDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.dto.PostImageDTO;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.service.CloudinaryService;
import org.example.apimywebsite.service.LikeService;
import org.example.apimywebsite.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.example.apimywebsite.api.model.Post;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/post")
public class PostController {
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostService postService;
    @Autowired
    private LikeService likeService;
    @Autowired
    private CloudinaryService cloudinaryService;

    @GetMapping("/posts")
    public ResponseEntity<List<PostDTO>> getPostByUser(
            @RequestParam long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request
    ) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            List<PostDTO> posts = postService.getPostsByUserDTO(userId, authHeader, page, size);
            return ResponseEntity.ok(posts);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/{userId}/all-post-images")
    public ResponseEntity<List<String>> getUserPostImages(
            @PathVariable int userId,
            @RequestHeader("Authorization") String authHeader
    ) {
        List<String> imageUrls = postService.getAllPostImageUrlsByUserId(userId, authHeader);
        return ResponseEntity.ok(imageUrls);
    }


@GetMapping("/feed")
public ResponseEntity<List<PostDTO>> getPostToFeed(
        HttpServletRequest request,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
) {
    try {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<PostDTO> posts = postService.getFeedPosts(authHeader, page, size);
        return ResponseEntity.ok(posts);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDTO> addPost(
            @RequestParam("postText") String postText,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            List<String> imageUrls = new ArrayList<>();

            if (files != null && files.size() > 4) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            // ✅ העלאת תמונות ל-Cloudinary ושמירת ה-URLs
            if (files != null) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String imageUrl = cloudinaryService.uploadImage(file);
                        imageUrls.add(imageUrl);
                    }
                }
            }

            // ✅ יצירת ושמירת הפוסט
            Post post = new Post();
            post.setPostText(postText);

            PostDTO postDTO = postService.addPost(post, authHeader, imageUrls);

            return ResponseEntity.ok(postDTO);

        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{postId}/like-count")
    public long getLikeCount(@PathVariable long postId) {
        Post post = postRepository.findByPostId(postId);
        return likeService.getLikeCountForPost(post);
    }

    @DeleteMapping("/delete/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable long postId, @RequestHeader("Authorization") String authHeader) {
        postService.deletePost(postId, authHeader);

        return ResponseEntity.ok().build();
    }
    @PutMapping("/edit/{postId}")
    public ResponseEntity<PostDTO> editPost(@PathVariable long postId, @RequestBody EditPostRequestDTO request, @RequestHeader("Authorization") String authHeader) {
        PostDTO updatedPost = postService.editPost(postId, request.getContent(), authHeader);
        return ResponseEntity.ok(updatedPost);
    }
}

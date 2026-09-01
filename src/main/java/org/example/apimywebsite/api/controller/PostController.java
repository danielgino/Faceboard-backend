package org.example.apimywebsite.api.controller;

import jakarta.validation.Valid;
import org.example.apimywebsite.dto.EditPostRequestDTO;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.service.CloudinaryService;
import org.example.apimywebsite.service.LikeService;
import org.example.apimywebsite.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import org.example.apimywebsite.api.model.Post;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/post")
public class PostController {
    private static final Logger log = LoggerFactory.getLogger(PostController.class);

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
            @RequestParam(defaultValue = "10") int size
    ) {
        try {
            List<PostDTO> posts = postService.getPostsByUserDTO(userId, page, size);
            return ResponseEntity.ok(posts);

        } catch (ResponseStatusException e) {
            // H8b: AuthHelper now throws a typed ResponseStatusException (401) instead of a
            // plain RuntimeException; let it propagate rather than swallowing it below.
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch posts for user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/{userId}/all-post-images")
    public ResponseEntity<List<String>> getUserPostImages(
            @PathVariable int userId) {
        List<String> imageUrls = postService.getAllPostImageUrlsByUserId(userId);
        return ResponseEntity.ok(imageUrls);
    }


@GetMapping("/feed")
public ResponseEntity<List<PostDTO>> getPostToFeed(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "5") int size
) {
    try {

        List<PostDTO> posts = postService.getFeedPosts( page, size);
        return ResponseEntity.ok(posts);

    } catch (ResponseStatusException e) {
        // H8b: AuthHelper now throws a typed ResponseStatusException (401) instead of a
        // plain RuntimeException; let it propagate rather than swallowing it below.
        throw e;
    } catch (Exception e) {
        log.error("Failed to fetch feed posts", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostDTO> addPost(
            @RequestParam("postText") String postText,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        // COR-010 fix: files are uploaded to Cloudinary one at a time before the post is ever
        // persisted. If a later file in the same request fails validation/upload, or the
        // subsequent postService.addPost call itself fails, every file already uploaded earlier
        // in this same request had no post/PostImage row to ever reference it - a permanent
        // orphan. imageUrls is declared outside the try so every catch below can compensate by
        // deleting exactly what THIS request uploaded before failing. These are always brand-new
        // uploads for a not-yet-created post (never a replacement of an existing asset), so there
        // is no "old asset" ordering concern here.
        List<String> imageUrls = new ArrayList<>();
        try {
            if (files != null && files.size() > 4) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            if (files != null) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String imageUrl = cloudinaryService.uploadImage(file);
                        imageUrls.add(imageUrl);
                    }
                }
            }
            Post post = new Post();
            post.setPostText(postText);
            PostDTO postDTO = postService.addPost(post, imageUrls);
            return ResponseEntity.ok(postDTO);

        } catch (ResponseStatusException e) {
            // H8b: CloudinaryService (bad file -> 400) and AuthHelper (unauthenticated -> 401,
            // via postService.addPost) now throw typed ResponseStatusExceptions instead of
            // RuntimeException/IllegalArgumentException; let them propagate instead of being
            // swallowed into the generic 404 fallback below, which is unchanged for any other
            // (non-migrated, unexpected) RuntimeException.
            imageUrls.forEach(cloudinaryService::deleteImage);
            throw e;
        } catch (RuntimeException e) {
            imageUrls.forEach(cloudinaryService::deleteImage);
            log.error("Failed to add post", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            imageUrls.forEach(cloudinaryService::deleteImage);
            log.error("Failed to add post", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{postId}/like-count")
    public long getLikeCount(@PathVariable long postId) {
        Post post = postRepository.findByPostId(postId);
        return likeService.getLikeCountForPost(post);
    }

    @DeleteMapping("/delete/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable long postId) {
        postService.deletePost(postId);

        return ResponseEntity.ok().build();
    }
    @PutMapping("/edit/{postId}")
    public ResponseEntity<PostDTO> editPost(@PathVariable long postId, @Valid @RequestBody EditPostRequestDTO request) {
        PostDTO updatedPost = postService.editPost(postId, request.getContent());
        return ResponseEntity.ok(updatedPost);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDTO> getPostById(@PathVariable long postId) {
        PostDTO dto = postService.getPostById(postId);
        return ResponseEntity.ok(dto);
    }

}

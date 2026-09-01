package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.service.CloudinaryService;
import org.example.apimywebsite.service.LikeService;
import org.example.apimywebsite.service.PostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H8b plumbing fix: PostController's local catches used to intercept ANY RuntimeException
 * from PostService/CloudinaryService (via AuthHelper, "Post not found", "Only image files are
 * allowed.", etc.) and collapse them into an empty-body 404/500. Now that those sites throw
 * typed ResponseStatusException instead, the controller must let it propagate to
 * GlobalExceptionHandler rather than swallowing it into the unchanged generic fallbacks below
 * (which still apply to any other, non-migrated RuntimeException exactly as before).
 */
@ExtendWith(MockitoExtension.class)
class PostControllerTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostService postService;
    @Mock
    private LikeService likeService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private PostController controller;

    @Test
    void getPostByUser_authHelperThrowsUnauthorized_propagatesUnchanged() {
        when(postService.getPostsByUserDTO(anyLong(), anyInt(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getPostByUser(1L, 0, 10));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getReason());
    }

    @Test
    void getPostToFeed_authHelperThrowsUnauthorized_propagatesUnchanged() {
        when(postService.getFeedPosts(anyInt(), anyInt()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getPostToFeed(0, 5));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getReason());
    }

    @Test
    void addPost_cloudinaryRejectsFile_propagatesUnchanged_notSwallowedInto404() throws Exception {
        MultipartFile file = new MockMultipartFile("files", "x.html", "text/html", "<html></html>".getBytes());
        when(cloudinaryService.uploadImage(any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed."));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addPost("hello", List.of(file)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only image files are allowed.", ex.getReason());
    }

    @Test
    void addPost_authHelperThrowsUnauthorized_propagatesUnchanged_notSwallowedInto404() {
        when(postService.addPost(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addPost("hello", null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getReason());
    }

    @Test
    void addPost_unrelatedUnexpectedRuntimeException_stillFallsBackTo404_unchangedBehavior() {
        // Regression guard: any RuntimeException NOT part of the H8b migration must still be
        // handled exactly as before (empty-body 404) - proves the fix is additive, not a
        // behavior change for unrelated/unexpected errors.
        when(postService.addPost(any(), any())).thenThrow(new RuntimeException("some other unexpected error"));

        var response = controller.addPost("hello", null);

        assertEquals(404, response.getStatusCode().value());
    }

    // ---- COR-010 fix: a file already uploaded to Cloudinary earlier in this same request must
    // not become a permanent orphan when a later step in the same request fails. ----

    @Test
    void addPost_secondFileFailsUpload_deletesTheFirstFilesAlreadyUploadedImage() throws Exception {
        MultipartFile first = new MockMultipartFile("files", "one.png", "image/png", "one".getBytes());
        MultipartFile second = new MockMultipartFile("files", "two.html", "text/html", "<html></html>".getBytes());
        when(cloudinaryService.uploadImage(first)).thenReturn("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        when(cloudinaryService.uploadImage(second)).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are allowed."));

        assertThrows(ResponseStatusException.class, () -> controller.addPost("hello", List.of(first, second)));

        verify(cloudinaryService).deleteImage("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        verify(postService, never()).addPost(any(), any());
    }

    @Test
    void addPost_postServiceFailsAfterUpload_deletesTheAlreadyUploadedImage() throws Exception {
        MultipartFile file = new MockMultipartFile("files", "one.png", "image/png", "one".getBytes());
        when(cloudinaryService.uploadImage(file)).thenReturn("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        when(postService.addPost(any(), any())).thenThrow(new RuntimeException("db connection reset"));

        controller.addPost("hello", List.of(file));

        verify(cloudinaryService).deleteImage("https://res.cloudinary.com/demo/image/upload/v1/one.png");
    }

    @Test
    void addPost_success_neverDeletesTheJustUploadedImages() throws Exception {
        MultipartFile file = new MockMultipartFile("files", "one.png", "image/png", "one".getBytes());
        when(cloudinaryService.uploadImage(file)).thenReturn("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        when(postService.addPost(any(), any())).thenReturn(mock(PostDTO.class));

        controller.addPost("hello", List.of(file));

        verify(cloudinaryService, never()).deleteImage(any());
    }
}

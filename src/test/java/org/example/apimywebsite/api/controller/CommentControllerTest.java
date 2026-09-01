package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.dto.AddCommentRequestDTO;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.service.CommentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * H8b plumbing fix: CommentController's local catch used to intercept ANY IllegalArgumentException
 * from CommentService and return an empty-body 404. Now that CommentService throws typed
 * ResponseStatusException instead, the controller must let it propagate to
 * GlobalExceptionHandler rather than swallowing it into the generic 500 fallback.
 */
@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentService commentService;

    private AddCommentRequestDTO aRequest() {
        AddCommentRequestDTO dto = new AddCommentRequestDTO();
        dto.setPostId(1L);
        dto.setText("hello");
        return dto;
    }

    @Test
    void addComment_serviceThrowsResponseStatusException_propagatesUnchanged_notSwallowedInto500() {
        CommentController controller = new CommentController(commentService);
        when(commentService.addComment(any(AddCommentRequestDTO.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addComment(aRequest()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
    }

    @Test
    void addComment_normalRequest_returnsCreatedCommentDTO() {
        CommentController controller = new CommentController(commentService);
        CommentDTO dto = new CommentDTO(1L, 1L, "Alice A", "alice", "hello", OffsetDateTime.now(), null);
        when(commentService.addComment(any(AddCommentRequestDTO.class))).thenReturn(dto);

        ResponseEntity<CommentDTO> response = controller.addComment(aRequest());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(dto, response.getBody());
    }
}

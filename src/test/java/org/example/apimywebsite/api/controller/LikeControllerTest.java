package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.service.LikeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * H8b plumbing fix: LikeController's local catch used to intercept ANY IllegalArgumentException
 * from LikeService and return a 404. Now that LikeService throws typed ResponseStatusException
 * instead, the controller must let it propagate to GlobalExceptionHandler rather than
 * swallowing it into the generic 500 fallback.
 * M-OOP1: LikeService.addLike no longer takes an HttpServletRequest - the acting user is
 * resolved internally via AuthHelper instead of manual Authorization-header parsing.
 */
@ExtendWith(MockitoExtension.class)
class LikeControllerTest {

    @Mock
    private LikeService likeService;

    @Test
    void addLike_serviceThrowsResponseStatusException_propagatesUnchanged_notSwallowedInto500() {
        LikeController controller = new LikeController(likeService);
        when(likeService.addLike(any(Like.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addLike(new Like()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
    }
}

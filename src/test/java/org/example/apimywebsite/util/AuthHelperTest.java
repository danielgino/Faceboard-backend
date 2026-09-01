package org.example.apimywebsite.util;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * H8b: AuthHelper.getCurrentUser() now throws a typed ResponseStatusException(401) for a
 * missing/unauthenticated SecurityContext instead of a plain RuntimeException("Unauthorized")
 * that the generic handler always mapped to 400.
 */
@ExtendWith(MockitoExtension.class)
class AuthHelperTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthHelper authHelper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_noAuthenticationInContext_throwsUnauthorized() {
        SecurityContextHolder.clearContext();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, authHelper::getCurrentUser);

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getReason());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUser_unauthenticatedToken_throwsUnauthorized() {
        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken("someone", "creds");
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, authHelper::getCurrentUser);

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Unauthorized", ex.getReason());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUser_authenticated_returnsResolvedUser() {
        // The 3-arg constructor (with an authorities collection, even empty) is the one that
        // marks the token authenticated; the 2-arg constructor used elsewhere in this file is
        // Spring Security's own convention for a not-yet-authenticated principal.
        UsernamePasswordAuthenticationToken authenticated =
                new UsernamePasswordAuthenticationToken("alice", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authenticated);
        User expected = User.builder().id(1).userName("alice").build();
        when(userRepository.findByUserName("alice")).thenReturn(expected);

        User result = authHelper.getCurrentUser();

        assertEquals(expected, result);
    }
}

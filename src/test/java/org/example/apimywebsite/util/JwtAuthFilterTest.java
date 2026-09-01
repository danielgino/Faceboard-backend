package org.example.apimywebsite.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for JwtAuthFilter (H3 fix): malformed, expired, or mis-signed JWTs must never
 * produce an unhandled exception — the request must simply remain unauthenticated so
 * Spring Security's own (unmodified) filter chain produces the normal 401, instead of
 * the JWT parsing exception surfacing as an unhandled 500. Real jjwt-generated tokens
 * are used throughout (not mocked exceptions) to prove the actual exception types thrown
 * by the library are the ones being caught.
 */
class JwtAuthFilterTest {

    private static final String SECRET_A = "unit-test-secret-A-long-enough-for-HMAC256-1234567890";
    private static final String SECRET_B = "unit-test-secret-B-long-enough-for-HMAC256-0987654321";
    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET_A);
    private final UserRepository userRepository = mock(UserRepository.class);

    private JwtAuthFilter newFilter() {
        JwtAuthFilter filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filter, "userRepository", userRepository);
        return filter;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private HttpServletRequest requestWithAuthHeader(String headerValue) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/post/feed");
        when(request.getHeader("Authorization")).thenReturn(headerValue);
        return request;
    }

    private void runFilter(HttpServletRequest request) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        newFilter().doFilterInternal(request, response, chain);
        // The filter must always hand off to the rest of the chain — it never blocks the
        // request itself; the 401/403 decision belongs to Spring Security's downstream filters.
        verify(chain).doFilter(request, response);
    }

    @Test
    void validToken_setsAuthenticatedPrincipal() throws Exception {
        when(userRepository.findByUserName("alice"))
                .thenReturn(User.builder().id(1).userName("alice").password(PASSWORD_HASH).build());
        String token = jwtUtil.generateToken("alice", PASSWORD_HASH);

        runFilter(requestWithAuthHeader("Bearer " + token));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    // ---- SEC-005 fix: a token issued before the user's password was changed/reset must stop
    // authenticating immediately, even though it is otherwise validly signed and unexpired. ----

    @Test
    void tokenIssuedBeforePasswordChange_doesNotAuthenticate_andDoesNotThrow() throws Exception {
        // Token was issued while the password hash was PASSWORD_HASH, but the user's current
        // (post-change/reset) password hash in the DB is now different.
        when(userRepository.findByUserName("alice"))
                .thenReturn(User.builder().id(1).userName("alice").password("$2a$10$brandNewHashAfterPasswordChange0000").build());
        String staleToken = jwtUtil.generateToken("alice", PASSWORD_HASH);

        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("Bearer " + staleToken)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenIssuedAfterPasswordChange_stillAuthenticates() throws Exception {
        // Sanity check for the fix above: a freshly-issued token (reflecting the CURRENT
        // password hash) must keep working normally - this isn't a blanket token rejection.
        when(userRepository.findByUserName("alice"))
                .thenReturn(User.builder().id(1).userName("alice").password(PASSWORD_HASH).build());
        String freshToken = jwtUtil.generateToken("alice", PASSWORD_HASH);

        runFilter(requestWithAuthHeader("Bearer " + freshToken));

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredToken_doesNotAuthenticate_andDoesNotThrow() throws Exception {
        String expiredToken = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET_A.getBytes()), Jwts.SIG.HS256)
                .compact();

        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("Bearer " + expiredToken)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedToken_doesNotAuthenticate_andDoesNotThrow() throws Exception {
        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("Bearer not-a-real-jwt")));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenSignedWithDifferentSecret_doesNotAuthenticate_andDoesNotThrow() throws Exception {
        JwtUtil otherIssuer = new JwtUtil(SECRET_B);
        String token = otherIssuer.generateToken("alice", PASSWORD_HASH);

        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("Bearer " + token)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingAuthorizationHeader_leavesRequestUnauthenticated_andContinuesChain() throws Exception {
        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader(null)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void malformedBearerHeader_isIgnoredGracefully() throws Exception {
        // No "Bearer " prefix at all — the filter must not attempt to parse it as a token.
        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("garbage-header-value")));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void emptyAuthorizationHeader_isIgnoredGracefully() throws Exception {
        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("")));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenWithCorruptedBase64Payload_doesNotAuthenticate_andDoesNotThrow() throws Exception {
        // Structurally a 3-segment JWT (unlike the plain "not-a-real-jwt" case above), but with
        // deliberately invalid Base64URL content in the payload segment. Empirically verified
        // against the real jjwt 0.12.6 parser: this throws io.jsonwebtoken.MalformedJwtException
        // (a JwtException subtype, so it is caught) rather than an uncaught, unrelated decoding
        // exception - confirming the catch clause covers this specific escape path too.
        String corruptedToken = "eyJhbGciOiJIUzI1NiJ9.!!!not-valid-base64!!!.sig";

        assertDoesNotThrow(() -> runFilter(requestWithAuthHeader("Bearer " + corruptedToken)));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}

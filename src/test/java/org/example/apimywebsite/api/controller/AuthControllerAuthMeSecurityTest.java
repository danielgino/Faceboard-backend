package org.example.apimywebsite.api.controller;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.configuration.SecurityConfig;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.PasswordResetService;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M-OOP1 (/auth/me completion pass): end-to-end proof, against the real SecurityConfig filter
 * chain (real JwtAuthFilter, real AuthHelper - only UserRepository/UserService/JwtUtil/
 * PasswordResetService mocked), of the actual externally-observable HTTP status for every
 * /auth/me scenario after migrating the controller from manual Authorization-header/JWT
 * re-parsing to AuthHelper.getCurrentUser(). Written specifically to settle - empirically, not
 * by reasoning about the code alone - whether a request with no Authorization header reaches
 * the controller with a null SecurityContext or a non-null AnonymousAuthenticationToken (which
 * would otherwise let AuthHelper's own isAuthenticated()-based guard fail to catch it): either
 * way, the controller's own null-check on AuthHelper.getCurrentUser()'s result (added
 * specifically for this permitAll() endpoint - the one AuthHelper consumer not already gated by
 * .anyRequest().authenticated()) must still resolve every non-success case to 401.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthHelper.class})
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class AuthControllerAuthMeSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private PasswordResetService passwordResetService;
    // Demo Mode: AuthController.demoLogin now depends on this directly, and SecurityConfig's
    // DemoAccessFilter (a Filter, auto-scanned into this @WebMvcTest slice like JwtAuthFilter
    // already is) needs it too - either way the context can't load without it.
    @MockBean
    private InMemoryRateLimiter rateLimiter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- 1. No Authorization header at all ----

    @Test
    void noAuthorizationHeader_returns401_notReachingUserService() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    // ---- 2. Malformed Bearer header (no recognizable "Bearer " prefix) ----

    @Test
    void malformedBearerPrefix_returns401_notReachingUserService() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "NotBearer garbage"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    // ---- 3. Well-formed "Bearer <token>" but the token itself is invalid/malformed ----

    @Test
    void invalidJwt_returns401_notReachingUserService() throws Exception {
        when(jwtUtil.extractUsername("bad-token")).thenThrow(new MalformedJwtException("bad token"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    // ---- 4. Expired JWT ----

    @Test
    void expiredJwt_returns401_notReachingUserService() throws Exception {
        when(jwtUtil.extractUsername("expired-token")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
    }

    // ---- 5. Valid JWT: reaches the controller, returns the same UserDTO as before ----

    @Test
    void validJwt_returnsUserDTO_unchangedResponseShape() throws Exception {
        when(jwtUtil.extractUsername("good-token")).thenReturn("alice");
        when(jwtUtil.isTokenValid("good-token", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        when(jwtUtil.matchesCurrentPassword(eq("good-token"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(userService.getUserDTOById(1)).thenReturn(
                UserDTO.builder().id(1).username("alice").name("Alice").lastname("A").build());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.fullName").value("Alice A"));
    }

    // ---- 6. No second JWT parse inside the controller path: JwtUtil.extractUsername is called
    // exactly once (by JwtAuthFilter), never again by/for the controller itself ----

    @Test
    void validJwt_jwtUtilExtractUsernameCalledExactlyOnce_byFilterOnly() throws Exception {
        when(jwtUtil.extractUsername("good-token")).thenReturn("alice");
        when(jwtUtil.isTokenValid("good-token", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        when(jwtUtil.matchesCurrentPassword(eq("good-token"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(userService.getUserDTOById(1)).thenReturn(UserDTO.builder().id(1).build());

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(jwtUtil, org.mockito.Mockito.times(1)).extractUsername(eq("good-token"));
    }

    // ---- API-001 fix: LoginRequestDTO previously had no constraints at all - a missing
    // password reached PasswordEncoder.matches(null, ...), which throws IllegalArgumentException
    // and fell through to GlobalExceptionHandler's generic 500, not a clean 400 for an ordinary
    // missing-field client mistake. @Valid must now reject it before userService is ever
    // touched. /auth/login is permitAll(), so this reaches the controller with no
    // Authorization header at all, same as every other case here. ----

    @Test
    void login_missingPassword_returns400_notReachingUserService() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@example.com\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void login_blankEmailAndPassword_returns400_notReachingUserService() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }
}

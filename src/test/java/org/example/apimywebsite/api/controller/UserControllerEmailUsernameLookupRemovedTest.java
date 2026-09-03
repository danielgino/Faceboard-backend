package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.configuration.SecurityConfig;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmed-dead-endpoint finding: GET /user/email and GET /user/username allowed arbitrary
 * exact-match account lookup/enumeration by any authenticated user, with zero frontend, backend,
 * or test consumers (repo-wide trace) and no legitimate use (no admin role exists anywhere in
 * this application). Both routes were removed entirely. UserService/UserRepository's
 * findByEmail/findByUserName are unaffected - they remain in active use internally - so those
 * are not touched or tested here; see UserServiceTest/AuthHelperTest/JwtAuthFilterTest/etc. for
 * their continued coverage.
 *
 * Uses the real SecurityFilterChain (not addFilters=false), matching
 * SecurityConfigHttpBasicTest/CommentControllerAddCommentAuthenticationTest's established
 * pattern, so both "blocked before authentication" and "no longer routes after authentication"
 * are proven against the real filter chain + real DispatcherServlet routing, not assumed.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class UserControllerEmailUsernameLookupRemovedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private AuthHelper authHelper;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtUtil jwtUtil;
    // Demo Mode: DemoAccessFilter (a Filter, so auto-scanned into this @WebMvcTest slice like
    // JwtAuthFilter already is) needs this dependency satisfied for the context to load at all.
    @MockBean
    private InMemoryRateLimiter rateLimiter;

    private void authenticateAsAlice() {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
    }

    // ---- anonymous callers cannot retrieve account data through either route ----

    @Test
    void getUserByEmail_unauthenticated_isBlocked_neverReachesController() throws Exception {
        mockMvc.perform(get("/user/email").param("email", "victim@example.com"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void getUserByUsername_unauthenticated_isBlocked_neverReachesController() throws Exception {
        mockMvc.perform(get("/user/username").param("userName", "victim"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    // ---- removed routes no longer resolve for authenticated callers (ordinary users included -
    // there being no handler at all means no authenticated caller, ordinary or otherwise, can
    // perform the lookup) ----

    @Test
    void getUserByEmail_authenticated_noLongerResolves_returns404_andServiceNeverCalled() throws Exception {
        authenticateAsAlice();

        mockMvc.perform(get("/user/email").param("email", "victim@example.com")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isNotFound());

        // The key differentiator from "old handler ran and found no matching email": the
        // service is never invoked at all, proving DispatcherServlet had no handler to call,
        // not that a handler ran and returned not-found.
        verifyNoInteractions(userService);
    }

    @Test
    void getUserByUsername_authenticated_noLongerResolves_returns404_andServiceNeverCalled() throws Exception {
        authenticateAsAlice();

        mockMvc.perform(get("/user/username").param("userName", "victim")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(userService);
    }
}

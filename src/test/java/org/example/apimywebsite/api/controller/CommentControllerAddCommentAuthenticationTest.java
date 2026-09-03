package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.configuration.SecurityConfig;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.CommentService;
import org.example.apimywebsite.util.InMemoryRateLimiter;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comment-hijacking finding: proves POST /comments/add-comment is still rejected by the real
 * SecurityFilterChain (not addFilters=false) when unauthenticated - the fix only changed the
 * request model/persistence path, not the endpoint's place in SecurityConfig's
 * .anyRequest().authenticated() rule.
 */
@WebMvcTest(CommentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class CommentControllerAddCommentAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtUtil jwtUtil;
    // Demo Mode: DemoAccessFilter (a Filter, so auto-scanned into this @WebMvcTest slice like
    // JwtAuthFilter already is) needs this dependency satisfied for the context to load at all.
    @MockBean
    private InMemoryRateLimiter rateLimiter;

    @Test
    void addComment_unauthenticated_isBlocked() throws Exception {
        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content("{\"postId\":1,\"text\":\"hello\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(commentService);
    }
}

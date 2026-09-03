package org.example.apimywebsite.configuration;

import org.example.apimywebsite.api.controller.UserController;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.UserDTO;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H4 + M-SEC1 fix: real SecurityFilterChain (SecurityConfig imported; WebConfig no longer exists
 * at all - L-OOP4 removed it, since a competing MVC-level CORS mapping was its only other
 * historical content and JwtUtil, its last remaining bean, is now a plain @Component), proving
 * via actual observed response headers - not source-text matching - that: (a)
 * SecurityConfig.corsConfigurationSource() remains the sole CORS authority with unchanged allowed
 * origins/methods/headers/credentials, (b) preflight is handled without requiring JWT
 * authentication, (c) ordinary protected requests still require authentication, and (d)
 * SessionCreationPolicy.STATELESS means no HttpSession is ever created and a session (even if one
 * existed) cannot carry authentication across requests.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class SecurityConfigCorsAndSessionTest {

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

    // ---- CORS: allowed origins, unchanged methods/headers/credentials ----

    @Test
    void preflight_localhostOrigin_receivesExactExpectedHeaders_noJwtRequired() throws Exception {
        mockMvc.perform(options("/user/id")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        // No Authorization header was sent at all - proves preflight isn't gated on JWT auth.
    }

    @Test
    void preflight_vercelOrigin_receivesExactExpectedHeaders() throws Exception {
        mockMvc.perform(options("/user/id")
                        .header("Origin", "https://faceboard-frontend.vercel.app")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://faceboard-frontend.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void preflight_disallowedOrigin_receivesNoAllowOriginHeader() throws Exception {
        MvcResult result = mockMvc.perform(options("/user/id")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();

        assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void ordinaryProtectedRequest_stillRequiresAuthentication_corsHeadersDoNotBypassIt() throws Exception {
        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    // Re-verification gap fix (H4 follow-up): none of the tests above exercise an AUTHENTICATED
    // actual/simple cross-origin request all the way to the controller - the closest one
    // (ordinaryProtectedRequest_... above) is deliberately unauthenticated (401) and never reaches
    // UserController. This is the one genuinely missing regression case identified while
    // re-verifying H4: it proves the final single-authority CORS config still allows a real,
    // authenticated, cross-origin request to reach the controller with the correct header, not
    // just that headers appear on requests that get rejected before the controller runs.
    @Test
    void authenticatedActualCrossOriginRequest_fromLocalhost_succeeds_withCorrectHeader_andReachesController() throws Exception {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here - this permissive stub represents a token whose
        // fingerprint still matches the user's current password (real fingerprint behavior is
        // covered separately by JwtUtilTest/JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());
        when(userService.getUserDTOById(1)).thenReturn(UserDTO.builder().id(1).build());

        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Bearer sometoken")
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        verify(userService).getUserDTOById(1);
    }

    // ---- Stateless session policy ----

    @Test
    void authenticatedRequest_succeeds_andNeverCreatesAnHttpSession() throws Exception {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here - this permissive stub represents a token whose
        // fingerprint still matches the user's current password (real fingerprint behavior is
        // covered separately by JwtUtilTest/JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());

        MvcResult result = mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isOk())
                .andReturn();

        assertNull(result.getRequest().getSession(false),
                "STATELESS must mean no HttpSession is ever created, even for a successful authenticated request");
        assertNull(result.getResponse().getHeader("Set-Cookie"),
                "no JSESSIONID or other session cookie should ever be issued");
    }

    @Test
    void reusingAnHttpSessionFromAnAuthenticatedRequest_grantsNoAuthenticationOnALaterRequestWithoutAJwt() throws Exception {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here - this permissive stub represents a token whose
        // fingerprint still matches the user's current password (real fingerprint behavior is
        // covered separately by JwtUtilTest/JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Bearer sometoken")
                        .session(session))
                .andExpect(status().isOk());

        // Same MockHttpSession object reused, but no Authorization header this time - if the
        // security context had been persisted into the session (the pre-STATELESS default
        // behavior), this would incorrectly succeed as "alice" with no JWT presented.
        mockMvc.perform(get("/user/id").param("id", "1")
                        .session(session))
                .andExpect(status().isUnauthorized());
    }
}

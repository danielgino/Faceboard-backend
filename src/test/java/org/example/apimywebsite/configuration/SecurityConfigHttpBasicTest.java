package org.example.apimywebsite.configuration;

import org.example.apimywebsite.api.controller.UserController;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.UserDTO;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * httpBasic() removal finding: SecurityConfig used to add a BasicAuthenticationFilter (backed
 * only by Spring Boot's auto-generated default user - confirmed via the evidence chain in
 * BACKEND_DEEP_AUDIT.md's follow-up: spring-boot-starter-security with no OAuth2/SAML dependency,
 * no @SpringBootApplication autoconfig exclusion, no spring.security.user.* override, and no
 * UserDetailsService/AuthenticationProvider/AuthenticationManager bean anywhere in the app).
 * These tests prove, against the real imported SecurityConfig filter chain (not a mock), that
 * Basic credentials can no longer authenticate while JWT Bearer auth, public endpoints, ownership
 * based 403s, and CORS preflight remain exactly as before.
 *
 * Baseline values asserted below (401/"Unauthorized"/XHR status-only/CORS preflight headers) were
 * captured from a real MockMvc run against the pre-edit, httpBasic()-enabled SecurityConfig before
 * this change was made, and cross-checked against the resolved Spring Security 6.4.2 sources for
 * BasicAuthenticationEntryPoint and HttpBasicConfigurer.
 *
 * MockMvc limitation: MockHttpServletResponse records sendError's status/message via
 * getStatus()/getErrorMessage(), but MockMvc does not perform the servlet container's automatic
 * forward to Boot's /error-mapped BasicErrorController, so getContentAsString() is empty here in
 * both the before- and after-edit state - it cannot be used to assert the final rendered JSON
 * body. getStatus()/getErrorMessage() are therefore the strongest claim MockMvc can support.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class SecurityConfigHttpBasicTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockBean
    private UserService userService;
    @MockBean
    private AuthHelper authHelper;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private JwtUtil jwtUtil;

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    // ---- 1. Authoritative structural proof ----

    @Test
    void effectiveChainForProtectedPath_hasExactlyOneMatch_andNoBasicAuthenticationFilter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/id");

        List<SecurityFilterChain> matching = filterChainProxy.getFilterChains().stream()
                .filter(chain -> chain.matches(request))
                .toList();

        assertEquals(1, matching.size(), "exactly one SecurityFilterChain must be applicable to /user/id");
        assertTrue(matching.get(0).getFilters().stream().noneMatch(BasicAuthenticationFilter.class::isInstance),
                "the chain governing /user/id must no longer contain a BasicAuthenticationFilter");
    }

    // ---- 2-4. Behavioral companions: same request shapes as the captured baseline ----

    @Test
    void noCredentials_ordinaryRequest_matchesBaseline_exceptNoChallenge() throws Exception {
        mockMvc.perform(get("/user/id").param("id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertEquals("Unauthorized", result.getResponse().getErrorMessage()))
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        verifyNoInteractions(userService);
        verify(userRepository, never()).findByUserName(anyString());
    }

    @Test
    void noCredentials_xmlHttpRequest_matchesBaselineExactly() throws Exception {
        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertNull(result.getResponse().getErrorMessage()))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void malformedBasicHeader_isRejected_viaSameOrdinaryEntryPoint_noException() throws Exception {
        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Basic not-valid-base64!!"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertEquals("Unauthorized", result.getResponse().getErrorMessage()))
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    // ---- 5. Well-formed Basic credentials, matching a real mocked user, still cannot authenticate ----

    @Test
    void wellFormedBasicCredentials_cannotAuthenticate() throws Exception {
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());

        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", basic("alice", "whatever-password")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService);
        // Only JwtAuthFilter ever calls findByUserName, and only for Bearer tokens - proving it
        // was never invoked shows the Basic header's value was never even inspected as a
        // potential identity, not merely that one credential pair was rejected.
        verify(userRepository, never()).findByUserName(anyString());
    }

    // ---- 6. JWT Bearer remains fully functional ----

    @Test
    void validJwtBearerToken_stillAuthenticates_reachesController() throws Exception {
        // /user/id is self-only (public-profile finding); the requested id must match the
        // authenticated principal for the request to reach the controller's success path.
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here (not a real JwtUtil), so this is just a permissive stub -
        // it doesn't assert anything about the actual fingerprint algorithm, only that this
        // scenario represents a token whose password fingerprint still matches the user's
        // current one (real fingerprint behavior is covered separately by JwtUtilTest/
        // JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());
        when(userService.getUserDTOById(1)).thenReturn(UserDTO.builder().id(1).username("alice").build());

        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isOk());
    }

    // ---- 6b. H6 fix: response body is genuinely the UserDTO shape, not the raw User entity ----

    @Test
    void validJwtBearerToken_selfLookup_returnsUserDtoJsonShape_notRawEntityFieldNames() throws Exception {
        // JSON-shape proof, not just a Java-type/mock-verification one: UserMapper maps the
        // entity's "userName" field to the DTO's "username" property
        // (@Mapping(source = "userName", target = "username")). If this endpoint ever
        // regressed to serializing the raw User entity again, the response would carry
        // "userName" (and would be missing the DTO-only "friendsList"/"fullName" shape), not
        // "username" - this test would catch that regression at the actual wire level.
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here (not a real JwtUtil), so this is just a permissive stub -
        // it doesn't assert anything about the actual fingerprint algorithm, only that this
        // scenario represents a token whose password fingerprint still matches the user's
        // current one (real fingerprint behavior is covered separately by JwtUtilTest/
        // JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());
        when(userService.getUserDTOById(1)).thenReturn(
                UserDTO.builder().id(1).username("alice").name("Alice").lastname("A").build());

        mockMvc.perform(get("/user/id").param("id", "1")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.userName").doesNotExist())
                .andExpect(jsonPath("$.fullName").value("Alice A"));
    }

    // ---- 6c. H6 fix: another user's id is rejected before any of their data is ever returned ----

    @Test
    void validJwtBearerToken_lookupOfAnotherUsersId_isForbidden_bodyCarriesNoTargetUserData() throws Exception {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here (not a real JwtUtil), so this is just a permissive stub -
        // it doesn't assert anything about the actual fingerprint algorithm, only that this
        // scenario represents a token whose password fingerprint still matches the user's
        // current one (real fingerprint behavior is covered separately by JwtUtilTest/
        // JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).userName("alice").build());

        mockMvc.perform(get("/user/id").param("id", "999")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isForbidden());

        // The 403 is returned before UserService is ever consulted about user 999 - no email,
        // birthDate, or any other field belonging to that other user is ever looked up, let
        // alone serialized.
        verifyNoInteractions(userService);
    }

    // ---- 7. Authenticated-but-forbidden (ownership check) is untouched, distinguishable from 401 ----

    @Test
    void authenticatedUser_withoutOwnership_returns403_notReinterpretedAs401() throws Exception {
        when(jwtUtil.extractUsername("sometoken")).thenReturn("alice");
        when(jwtUtil.isTokenValid("sometoken", "alice")).thenReturn(true);
        // SEC-005: JwtAuthFilter now also requires matchesCurrentPassword to authenticate.
        // jwtUtil is a full mock here (not a real JwtUtil), so this is just a permissive stub -
        // it doesn't assert anything about the actual fingerprint algorithm, only that this
        // scenario represents a token whose password fingerprint still matches the user's
        // current one (real fingerprint behavior is covered separately by JwtUtilTest/
        // JwtAuthFilterTest).
        when(jwtUtil.matchesCurrentPassword(eq("sometoken"), any())).thenReturn(true);
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(5).userName("alice").build());
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(5).userName("alice").build());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/user/99/profile-picture")
                        .file(file)
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isForbidden());

        verify(authHelper).getCurrentUser();
        verifyNoInteractions(userService);
    }

    // ---- 8. Public endpoints remain reachable without credentials ----

    @Test
    void publicEndpoint_reachableWithoutCredentials() throws Exception {
        mockMvc.perform(post("/user/register")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() != 401 && result.getResponse().getStatus() != 403));
    }

    // ---- 9. CORS preflight unchanged ----

    @Test
    void corsPreflight_behaviorUnchanged() throws Exception {
        mockMvc.perform(options("/user/id")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}

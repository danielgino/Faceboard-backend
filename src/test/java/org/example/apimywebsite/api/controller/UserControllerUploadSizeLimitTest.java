package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.UserService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload-size-limit finding: proves the profile-picture upload endpoint remains fully
 * functional for files at/near the configured spring.servlet.multipart.max-file-size=5MB
 * boundary and for ordinary small images.
 *
 * A genuine end-to-end test of the REJECTION path (a file over the limit -> 413) was
 * attempted here first and deliberately removed: MockMvc's multipart() request builder
 * constructs an already-resolved MultipartHttpServletRequest, so DispatcherServlet.
 * checkMultipart() never invokes the real StandardServletMultipartResolver/servlet-container
 * parsing that spring.servlet.multipart.max-file-size actually enforces - a file "one byte
 * over the limit" is silently accepted by MockMvc regardless of the configured property, which
 * was confirmed empirically (the request reached the controller instead of failing multipart
 * parsing). Exercising the real rejection path would require a full embedded-server test
 * (@SpringBootTest(webEnvironment = RANDOM_PORT)), which needs the application's real
 * datasource beans and therefore the unavailable DB_URL in this sandbox - the same
 * constraint already documented for ApiMyWebsiteApplicationTests.contextLoads throughout
 * this project. The rejection behavior is instead covered by:
 *   - GlobalExceptionHandlerTest's direct unit tests of handleMaxUploadSizeExceededException
 *     (413 status, exact safe body, no leaked detail), and
 *   - MultipartSizeLimitPropertiesTest, which pins the exact configured boundary values.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class UserControllerUploadSizeLimitTest {

    private static final int FIVE_MB = 5 * 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private AuthHelper authHelper;
    // Not used directly by these tests, but JwtAuthFilter (swept into this @WebMvcTest slice
    // as a Filter bean even though addFilters=false means it never actually runs) requires it.
    @MockBean
    private UserRepository userRepository;
    // H4 fix: WebConfig no longer implements WebMvcConfigurer (its addCorsMappings was the
    // duplicate CORS source, now removed), so it's no longer auto-swept into @WebMvcTest slices
    // - this JwtUtil bean (previously supplied incidentally via that sweep, per the removed
    // comment that used to sit here) must now be mocked explicitly.
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void uploadProfilePicture_fileAtConfiguredLimit_isAccepted_andReachesService() throws Exception {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        when(userService.uploadProfilePicture(eq(1), any())).thenReturn("https://cdn.example.com/pic.png");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[FIVE_MB]);

        mockMvc.perform(multipart("/user/1/profile-picture").file(file))
                .andExpect(status().isOk());

        verify(userService).uploadProfilePicture(eq(1), any());
    }

    @Test
    void uploadProfilePicture_validSmallImage_remainsCompatible() throws Exception {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        when(userService.uploadProfilePicture(eq(1), any())).thenReturn("https://cdn.example.com/pic.png");
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/user/1/profile-picture").file(file))
                .andExpect(status().isOk());

        verify(userService).uploadProfilePicture(eq(1), any());
    }
}

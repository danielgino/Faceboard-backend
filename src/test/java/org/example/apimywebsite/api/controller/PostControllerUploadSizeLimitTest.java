package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.CloudinaryService;
import org.example.apimywebsite.service.LikeService;
import org.example.apimywebsite.service.PostService;
import org.example.apimywebsite.util.InMemoryRateLimiter;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload-size-limit finding: PostController.addPost (the only multiple-file upload endpoint)
 * relies on the same two Spring properties as the single-file endpoints for both per-file
 * (spring.servlet.multipart.max-file-size=5MB) and total-request
 * (spring.servlet.multipart.max-request-size=10MB) enforcement - it has no size-checking code
 * of its own, so there is no additional application-level enforcement path to test beyond what
 * GlobalExceptionHandlerTest and MultipartSizeLimitPropertiesTest already cover. This class
 * proves the multi-file path itself remains compatible: several valid small images are still
 * accepted and reach CloudinaryService/PostService exactly as before. See
 * UserControllerUploadSizeLimitTest's Javadoc for why a live over-the-limit MockMvc test isn't
 * possible in this environment.
 */
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "JWT_SECRET=test-only-placeholder-not-a-real-secret")
class PostControllerUploadSizeLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostRepository postRepository;
    @MockBean
    private PostService postService;
    @MockBean
    private LikeService likeService;
    @MockBean
    private CloudinaryService cloudinaryService;
    @MockBean
    private UserRepository userRepository;
    // H4 fix: WebConfig no longer implements WebMvcConfigurer (its addCorsMappings was the
    // duplicate CORS source, now removed), so it's no longer auto-swept into @WebMvcTest slices
    // - this JwtUtil bean (previously supplied incidentally via that sweep) must now be mocked
    // explicitly, matching every other @WebMvcTest slice in this suite.
    @MockBean
    private JwtUtil jwtUtil;
    // Demo Mode: DemoAccessFilter (a Filter, so swept into this slice's beans even though
    // addFilters=false means it never actually runs) needs this to be constructable.
    @MockBean
    private InMemoryRateLimiter rateLimiter;

    @Test
    void addPost_multipleValidSmallImages_remainsCompatible_andReachesCloudinaryForEach() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "a.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile file2 = new MockMultipartFile("files", "b.png", "image/png", new byte[]{4, 5, 6});
        MockMultipartFile file3 = new MockMultipartFile("files", "c.png", "image/png", new byte[]{7, 8, 9});
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/img.png");
        when(postService.addPost(any(), any())).thenReturn(null);

        mockMvc.perform(multipart("/post/add")
                        .file(file1).file(file2).file(file3)
                        .param("postText", "hello"))
                .andExpect(status().isOk());

        verify(cloudinaryService, times(3)).uploadImage(any());
    }

    @Test
    void addPost_moreThanFourFiles_stillReturns400_existingCountLimitUnchanged() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("files", "1.png", "image/png", new byte[]{1});
        MockMultipartFile f2 = new MockMultipartFile("files", "2.png", "image/png", new byte[]{2});
        MockMultipartFile f3 = new MockMultipartFile("files", "3.png", "image/png", new byte[]{3});
        MockMultipartFile f4 = new MockMultipartFile("files", "4.png", "image/png", new byte[]{4});
        MockMultipartFile f5 = new MockMultipartFile("files", "5.png", "image/png", new byte[]{5});

        mockMvc.perform(multipart("/post/add")
                        .file(f1).file(f2).file(f3).file(f4).file(f5)
                        .param("postText", "hello"))
                .andExpect(status().isBadRequest());
    }
}

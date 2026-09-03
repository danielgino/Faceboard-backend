package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.repository.UserRepository;
import org.example.apimywebsite.service.CommentService;
import org.example.apimywebsite.service.NotificationService;
import org.example.apimywebsite.util.AuthHelper;
import org.example.apimywebsite.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comment-hijacking finding: end-to-end proof against the real CommentController + real
 * CommentService + real AuthHelper (M-OOP1 - CommentService now resolves its acting user via
 * AuthHelper rather than a direct UserRepository lookup; only CommentRepository/UserRepository/
 * PostRepository/NotificationService are mocked, and AuthHelper is wired from the same mocked
 * UserRepository) that a malicious payload containing every field the old raw-Comment binding
 * used to accept can no longer reach a persisted Comment. addFilters=false + a manually-set
 * SecurityContextHolder (matching CommentServiceTest's established pattern) isolates this from
 * the security filter chain itself, which is proven separately in
 * CommentControllerAddCommentAuthenticationTest.
 *
 * Verification limitation (reported per instructions, not glossed over): this proves, via a
 * captured argument, that a freshly-constructed Comment (id 0) is the only thing ever passed to
 * CommentRepository.save() - it does not exercise a real Hibernate merge-vs-persist decision
 * against a real database, since this environment has no DB_URL-backed test infrastructure (the
 * same, already-documented limitation affecting the rest of this suite). Real JPA
 * persist/merge behavior for GenerationType.IDENTITY + a manually-set nonzero id is Spring Data
 * JPA's own documented, version-independent default (AbstractEntityInformation.isNew), not
 * something re-verified against a live database here.
 */
@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({CommentService.class, AuthHelper.class})
class CommentControllerAddCommentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentRepository commentRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private PostRepository postRepository;
    @MockBean
    private NotificationService notificationService;
    @MockBean
    private JwtUtil jwtUtil; // never exercised (addFilters=false), only needed to construct JwtAuthFilter's bean
    @MockBean
    private org.example.apimywebsite.util.InMemoryRateLimiter rateLimiter; // same reason, for DemoAccessFilter's bean

    private static final String MALICIOUS_PAYLOAD = """
            {
              "postId": 1,
              "text": "new comment",
              "commentId": 9999,
              "user": {
                "id": 500,
                "userName": "attacker-selected-user"
              },
              "createdAt": "2000-01-01T00:00:00",
              "post": {
                "postId": 999
              }
            }
            """;

    // M-OOP1: CommentService now resolves its acting user via AuthHelper, which (correctly,
    // pre-existing/unchanged) requires Authentication.isAuthenticated()==true. The 2-arg
    // UsernamePasswordAuthenticationToken constructor used here previously produced an
    // authenticated=false token; the old direct-SecurityContextHolder code never checked that
    // flag, so it went unnoticed. The 3-arg constructor (with authorities) matches what
    // JwtAuthFilter actually constructs for a real authenticated request.
    private void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addComment_maliciousPayload_everyPreviouslyBindableFieldIsInert() throws Exception {
        User authenticatedUser = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        when(userRepository.findByUserName("alice")).thenReturn(authenticatedUser);
        Post realPost = new Post();
        realPost.setPostId(1L);
        realPost.setUser(User.builder().id(2).build());
        when(postRepository.findById(1L)).thenReturn(Optional.of(realPost));
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        when(commentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        loginAs("alice");

        // Established success contract is exactly 200 OK (not 201) - asserted precisely.
        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content(MALICIOUS_PAYLOAD))
                .andExpect(status().isOk());

        // The service resolved postId 1 (the top-level, allowlisted field) - never 999 (the
        // nested, spoofed post.postId) - proving that nested object was never bound/consulted.
        verify(postRepository).findById(1L);
        verify(postRepository, never()).findById(999L);

        Comment saved = captor.getValue();
        assertEquals(0L, saved.getCommentId(), "commentId=9999 in the payload must not reach the saved entity");
        assertEquals(authenticatedUser, saved.getUser(), "author must be the authenticated caller, not the spoofed user object");
        assertEquals(1, saved.getUser().getId(), "author id must be the real authenticated user's id, not the spoofed 500");
        assertNotEquals(2000, saved.getCreatedAt().getYear(), "createdAt=2000-01-01 in the payload must not reach the saved entity");
        assertEquals("new comment", saved.getText());

        // No existing comment was ever looked up by id, and save() was called exactly once -
        // proving no existing row was passed to save() or modified.
        verify(commentRepository, never()).findById(anyLong());
        verify(commentRepository, times(1)).save(any());
    }

    @Test
    void addComment_missingPostId_returns400_andNeverSaves() throws Exception {
        loginAs("alice");

        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content("{\"text\":\"hello\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentRepository);
    }

    // @NotBlank contract change (approved): the frontend already prevents empty submissions,
    // but the backend now also rejects null/empty/whitespace-only text explicitly.
    @Test
    void addComment_blankText_returns400_andNeverSaves() throws Exception {
        loginAs("alice");

        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content("{\"postId\":1,\"text\":\"   \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(commentRepository);
    }

    @Test
    void addComment_unknownPostId_returns404_establishedErrorBehavior() throws Exception {
        when(userRepository.findByUserName("alice")).thenReturn(User.builder().id(1).build());
        when(postRepository.findById(42L)).thenReturn(Optional.empty());
        loginAs("alice");

        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content("{\"postId\":42,\"text\":\"hello\"}"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(commentRepository);
    }

    // Product-policy finding: profile-wall commenting requires no accepted friendship - proven
    // here by a commenter and a post owner with no friendship of any kind set up anywhere in
    // this test, and the request still succeeding.
    @Test
    void addComment_authenticatedNonFriendOfPostOwner_stillSucceeds() throws Exception {
        User commenter = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        when(userRepository.findByUserName("alice")).thenReturn(commenter);
        Post post = new Post();
        post.setPostId(5L);
        post.setUser(User.builder().id(99).build());
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        loginAs("alice");

        mockMvc.perform(post("/comments/add-comment")
                        .contentType("application/json")
                        .content("{\"postId\":5,\"text\":\"nice post!\"}"))
                .andExpect(status().isOk());
    }
}

package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.AddCommentRequestDTO;
import org.example.apimywebsite.dto.CommentDTO;
import org.example.apimywebsite.repository.CommentRepository;
import org.example.apimywebsite.repository.PostRepository;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * H8b: CommentService.addComment now throws typed ResponseStatusException(404) instead of
 * ambiguous IllegalArgumentException, and every failure path must produce no comment save.
 * (deleteComment already used ResponseStatusException before this task and is unchanged -
 * covered separately by GlobalExceptionHandlerTest's existing integration test.)
 *
 * Comment-hijacking finding: addComment now takes the allowlisted AddCommentRequestDTO
 * (postId, text only) instead of a client-deserialized Comment entity - see
 * CommentControllerAddCommentSecurityTest for the end-to-end malicious-payload proof.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AuthHelper authHelper;
    @Mock
    private PostRepository postRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommentService commentService;

    private void loginAs(User user) {
        when(authHelper.getCurrentUser()).thenReturn(user);
    }

    private AddCommentRequestDTO requestFor(long postId, String text) {
        AddCommentRequestDTO dto = new AddCommentRequestDTO();
        dto.setPostId(postId);
        dto.setText(text);
        return dto;
    }

    @Test
    void addComment_unknownUser_throwsNotFound_andNeverSaves() {
        // AuthHelper itself does not null-check the repository lookup it performs - this
        // simulates a valid, authenticated JWT whose username no longer resolves to a User.
        loginAs(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(requestFor(1L, "hello")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("User not found", ex.getReason());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_postDoesNotExist_throwsNotFound_andNeverSaves() {
        loginAs(User.builder().id(1).userName("alice").build());
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.addComment(requestFor(99L, "hello")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_validRequest_succeeds_andSaves() {
        User user = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        loginAs(user);
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(requestFor(1L, "hello"));

        verify(commentRepository).save(any(Comment.class));
    }

    // ---- Comment-hijacking finding: structural proof at the service level ----

    @Test
    void addComment_savedEntityHasZeroIdBeforeSave_isFreshlyConstructed() {
        User user = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        loginAs(user);
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        when(commentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(requestFor(1L, "hello"));

        Comment saved = captor.getValue();
        assertEquals(0L, saved.getCommentId(), "a freshly constructed Comment must have the default/new-entity id");
    }

    @Test
    void addComment_authorAndTimestampAreServerControlled_notClientSupplied() {
        User authenticatedUser = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        loginAs(authenticatedUser);
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        when(commentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        OffsetDateTime before = OffsetDateTime.now();

        // AddCommentRequestDTO structurally has no user/author or createdAt field at all -
        // there is nothing for a caller to even attempt to set here.
        commentService.addComment(requestFor(1L, "hello"));

        OffsetDateTime after = OffsetDateTime.now();
        Comment saved = captor.getValue();
        assertEquals(authenticatedUser, saved.getUser(), "author must be the authenticated principal");
        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.getCreatedAt().isBefore(before), "createdAt must not predate the call");
        assertFalse(saved.getCreatedAt().isAfter(after), "createdAt must not postdate the call");
    }

    @Test
    void addComment_postResolvedServerSide_viaPostRepository() {
        loginAs(User.builder().id(1).userName("alice").build());
        Post post = new Post();
        post.setPostId(7L);
        post.setUser(User.builder().id(2).build());
        when(postRepository.findById(7L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(requestFor(7L, "hello"));

        verify(postRepository).findById(7L);
    }

    // ---- M-OOP1: acting user now resolved via AuthHelper instead of duplicating
    // SecurityContextHolder/findByUserName here ----

    @Test
    void addComment_actingUserComesFromAuthHelper() {
        User user = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        loginAs(user);
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.addComment(requestFor(1L, "hello"));

        verify(authHelper).getCurrentUser();
    }

    @Test
    void deleteComment_actingUserComesFromAuthHelper_ownerCanDeleteOwnComment() {
        User owner = User.builder().id(1).build();
        loginAs(owner);
        Comment comment = new Comment();
        comment.setUser(owner);
        Post post = new Post();
        post.setUser(User.builder().id(2).build());
        comment.setPost(post);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(5L);

        verify(authHelper).getCurrentUser();
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_unrelatedUser_isForbidden_andNeverDeletes() {
        loginAs(User.builder().id(99).build());
        Comment comment = new Comment();
        comment.setUser(User.builder().id(1).build());
        Post post = new Post();
        post.setUser(User.builder().id(2).build());
        comment.setPost(post);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(comment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> commentService.deleteComment(5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(commentRepository, never()).delete(any());
    }

    // ---- L-DB4C: getCommentsByPostId was previously unbounded
    // (findCommentsByPostIdWithUser with no Pageable). It now normalizes page/size, bounds the
    // query at the database level via Pageable, and reverses the DESC-ordered repository result
    // back to the external ASC (chronological) contract. ----

    private Comment aComment(long id, OffsetDateTime createdAt) {
        Comment c = new Comment();
        c.setCommentId(id);
        c.setUser(User.builder().id(1).userName("alice").name("Alice").lastname("A").build());
        c.setText("text-" + id);
        c.setCreatedAt(createdAt);
        return c;
    }

    @Test
    void getCommentsByPostId_noParams_defaultsToPageZeroAndDefaultSize() {
        loginAs(User.builder().id(1).build());
        when(commentRepository.findCommentsByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        commentService.getCommentsByPostId(1L, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findCommentsByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(CommentService.DEFAULT_COMMENTS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getCommentsByPostId_oversizedSize_clampedToMax() {
        loginAs(User.builder().id(1).build());
        when(commentRepository.findCommentsByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        commentService.getCommentsByPostId(1L, 0, Integer.MAX_VALUE);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findCommentsByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(CommentService.MAX_COMMENTS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getCommentsByPostId_negativePageAndSize_normalizeToDefaults() {
        loginAs(User.builder().id(1).build());
        when(commentRepository.findCommentsByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        commentService.getCommentsByPostId(1L, -5, -10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findCommentsByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(CommentService.DEFAULT_COMMENTS_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getCommentsByPostId_defaultPage_returnsMostRecentBatch_inChronologicalOrder() {
        loginAs(User.builder().id(1).build());
        // Repository returns newest-first (matches the real DESC query) - page 0 is the most
        // recent N comments, exactly like a real bounded query would produce.
        OffsetDateTime t1 = OffsetDateTime.now().minusMinutes(3);
        OffsetDateTime t2 = OffsetDateTime.now().minusMinutes(2);
        OffsetDateTime t3 = OffsetDateTime.now().minusMinutes(1);
        List<Comment> newestFirst = new ArrayList<>(List.of(aComment(3, t3), aComment(2, t2), aComment(1, t1)));
        when(commentRepository.findCommentsByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(newestFirst);

        List<CommentDTO> result = commentService.getCommentsByPostId(1L, null, null);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(CommentDTO::getCommentId).toList(),
                "response must be reversed back to oldest -> newest for the existing frontend contract");
    }

    @Test
    void getCommentsByPostId_subsequentPage_retrievesOlderComments() {
        loginAs(User.builder().id(1).build());
        when(commentRepository.findCommentsByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(aComment(1, OffsetDateTime.now().minusDays(1)))));

        List<CommentDTO> result = commentService.getCommentsByPostId(1L, 1, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(commentRepository).findCommentsByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(1L, result.get(0).getCommentId());
    }
}

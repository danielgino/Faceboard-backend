package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.LikeDTO;
import org.example.apimywebsite.repository.LikeRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * M-OOP1: LikeService.addLike(Like) previously re-parsed the raw Authorization header/JWT
 * manually (Pattern C) even though JwtAuthFilter already validates the token and populates
 * SecurityContextHolder before this code runs. It now resolves the acting user via the
 * canonical AuthHelper, matching every other authenticated business service (PostService,
 * NotificationService, CommentService). H8b's typed-exception behavior (404 on missing post)
 * is unchanged.
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private AuthHelper authHelper;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LikeService likeService;

    private Like likeFor(long postId) {
        Post post = new Post();
        post.setPostId(postId);
        Like like = new Like();
        like.setPost(post);
        return like;
    }

    @Test
    void addLike_postDoesNotExist_throwsNotFound_andNeverSaves() {
        User user = User.builder().id(1).build();
        when(authHelper.getCurrentUser()).thenReturn(user);
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> likeService.addLike(likeFor(99L)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
        verify(likeRepository, never()).save(any());
    }

    @Test
    void addLike_notYetLiked_addsLike_returnsAddedMessage() {
        User user = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(authHelper.getCurrentUser()).thenReturn(user);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPost_PostIdAndUser_Id(1L, 1)).thenReturn(false);

        String result = likeService.addLike(likeFor(1L));

        assertEquals("Like added !", result);
        verify(likeRepository).save(any());
    }

    @Test
    void addLike_alreadyLiked_removesLike_returnsRemovedMessage() {
        User user = User.builder().id(1).build();
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(authHelper.getCurrentUser()).thenReturn(user);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPost_PostIdAndUser_Id(1L, 1)).thenReturn(true);

        String result = likeService.addLike(likeFor(1L));

        assertEquals("Like Removed", result);
        verify(likeRepository).deleteByPost_PostIdAndUser_Id(1L, 1);
        verify(likeRepository, never()).save(any());
    }

    @Test
    void addLike_actingUserComesFromAuthHelper_notFromRequestBody() {
        // The client-deserialized Like carries its own settable `user` field, but it must never
        // be read for persistence - the like owner is always AuthHelper's authenticated user.
        User actingUser = User.builder().id(1).userName("alice").name("Alice").lastname("A").build();
        User spoofedOwner = User.builder().id(999).userName("victim").build();
        Post post = new Post();
        post.setPostId(1L);
        post.setUser(User.builder().id(2).build());
        when(authHelper.getCurrentUser()).thenReturn(actingUser);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(likeRepository.existsByPost_PostIdAndUser_Id(1L, 1)).thenReturn(false);

        Like spoofedLike = likeFor(1L);
        spoofedLike.setUser(spoofedOwner);
        likeService.addLike(spoofedLike);

        ArgumentCaptor<Like> captor = ArgumentCaptor.forClass(Like.class);
        verify(likeRepository).save(captor.capture());
        assertEquals(actingUser, captor.getValue().getUser());
    }

    // ---- L-DB4C: getUserLikesByPostId was previously unbounded
    // (findByPostIdWithUser with no Pageable). It now normalizes page/size and bounds the query
    // at the database level via Pageable, mirroring L-DB4B's notification pagination. ----

    private Like aLike(User user) {
        Like l = new Like();
        l.setUser(user);
        Post post = new Post();
        post.setPostId(1L);
        l.setPost(post);
        return l;
    }

    @Test
    void getUserLikesByPostId_noParams_defaultsToPageZeroAndDefaultSize() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        when(likeRepository.findByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        likeService.getUserLikesByPostId(1L, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(likeRepository).findByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(LikeService.DEFAULT_LIKES_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getUserLikesByPostId_oversizedSize_clampedToMax() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        when(likeRepository.findByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>());

        likeService.getUserLikesByPostId(1L, 0, Integer.MAX_VALUE);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(likeRepository).findByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(LikeService.MAX_LIKES_PAGE_SIZE, captor.getValue().getPageSize());
    }

    @Test
    void getUserLikesByPostId_subsequentPage_retrievesMoreLikers() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        User olderLiker = User.builder().id(42).userName("bob").name("Bob").lastname("B").build();
        when(likeRepository.findByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(aLike(olderLiker))));

        List<LikeDTO> result = likeService.getUserLikesByPostId(1L, 1, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(likeRepository).findByPostIdWithUser(eq(1L), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(42, result.get(0).getUserId());
    }

    @Test
    void getUserLikesByPostId_preservesExistingDTOShape() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(1).build());
        User liker = User.builder().id(7).userName("carol").name("Carol").lastname("C")
                .profilePictureUrl("http://example.com/pic.png").build();
        when(likeRepository.findByPostIdWithUser(eq(1L), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(aLike(liker))));

        List<LikeDTO> result = likeService.getUserLikesByPostId(1L, null, null);

        LikeDTO dto = result.get(0);
        assertEquals(7, dto.getUserId());
        assertEquals("carol", dto.getUsername());
        assertEquals(liker.getFullName(), dto.getFullName());
        assertEquals("http://example.com/pic.png", dto.getProfilePictureUrl());
    }
}

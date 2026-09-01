package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.mapper.PostMapper;
import org.example.apimywebsite.repository.*;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for PostService.getFeedPosts (H7 fix): the feed's author set must come from
 * accepted friendships only, sourced exclusively from FriendshipService.getAcceptedFriends. A
 * pending (not-yet-accepted) connection must never leak the other party's posts.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PostImageRepository postImageRepository;
    @Mock
    private AuthHelper authHelper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PostMapper postMapper;
    @Mock
    private FriendshipService friendshipService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private PostService postService;

    private static final int SELF_ID = 1;
    private static final int ACCEPTED_FRIEND_ID = 2;
    private static final int PENDING_ONLY_ID = 3;

    // M-DUP2: these two tests previously populated the now-removed User.friends @ManyToMany
    // field as a decoy (an unfiltered-by-status connection that a buggy feed implementation
    // might incorrectly read) to prove getFeedPosts ignored it. That field no longer exists on
    // User at all, so the decoy mechanism is gone - the real H7 invariant (the feed's author set
    // comes exclusively from FriendshipService.getAcceptedFriends, nothing else) is now proven
    // directly via interaction verification instead.

    @Test
    void getFeedPosts_includesSelfAndOnlyAcceptedFriends() {
        User acceptedFriend = User.builder().id(ACCEPTED_FRIEND_ID).build();
        User currentUser = User.builder().id(SELF_ID).build();

        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(friendshipService.getAcceptedFriends(currentUser)).thenReturn(List.of(acceptedFriend));
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getFeedPosts(0, 10);

        ArgumentCaptor<List<Integer>> userIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).findAllPostsWithImages(userIdsCaptor.capture(), any(Pageable.class));
        assertEquals(List.of(SELF_ID, ACCEPTED_FRIEND_ID), userIdsCaptor.getValue());
        verify(friendshipService).getAcceptedFriends(currentUser);
    }

    @Test
    void getFeedPosts_excludesPendingOnlyConnections_whenAcceptedFriendsServiceReportsNone() {
        User currentUser = User.builder().id(SELF_ID).build();

        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        // Status-aware source correctly reports no accepted friends yet (e.g. a pending-only
        // connection, id PENDING_ONLY_ID, exists but is not yet accepted).
        when(friendshipService.getAcceptedFriends(currentUser)).thenReturn(List.of());
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getFeedPosts(0, 10);

        ArgumentCaptor<List<Integer>> userIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).findAllPostsWithImages(userIdsCaptor.capture(), any(Pageable.class));
        assertEquals(List.of(SELF_ID), userIdsCaptor.getValue());
        assertFalse(userIdsCaptor.getValue().contains(PENDING_ONLY_ID));
    }

    // ---- H8b: editPost/deletePost/getPostById now throw typed ResponseStatusExceptions
    // (403/404) instead of ambiguous plain RuntimeException, and forbidden actions must
    // produce no repository write/delete side effect. ----

    private static final long POST_ID = 42L;

    private Post postOwnedBy(int ownerId) {
        User owner = User.builder().id(ownerId).build();
        Post post = new Post();
        post.setUser(owner);
        post.setImages(new HashSet<>());
        return post;
    }

    @Test
    void editPost_byNonOwner_throwsForbidden_andNeverSaves() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(ACCEPTED_FRIEND_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> postService.editPost(POST_ID, "new text"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You are not authorized to edit this post", ex.getReason());
        verify(postRepository, never()).save(any());
    }

    @Test
    void editPost_postNotFound_throwsNotFound() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(SELF_ID).build());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> postService.editPost(POST_ID, "new text"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
        verify(postRepository, never()).save(any());
    }

    @Test
    void editPost_byOwner_succeeds_andSaves() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postMapper.toDto(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(null);

        postService.editPost(POST_ID, "new text");

        verify(postRepository).save(post);
    }

    // COR-006 fix: editPost previously hard-coded likeCount=0/commentCount=0 in the returned
    // DTO regardless of the post's real engagement, making a successful edit look like every
    // like/comment had vanished. It must now fetch and pass through the real counts, exactly
    // like getPostById already does.
    @Test
    void editPost_byOwner_returnsRealLikeAndCommentCounts_notFabricatedZeros() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.countLikesByPostId(post.getPostId())).thenReturn(7);
        when(commentRepository.countCommentsByPostId(post.getPostId())).thenReturn(3);
        when(likeRepository.existsByPost_PostIdAndUser_Id(post.getPostId(), SELF_ID)).thenReturn(true);

        postService.editPost(POST_ID, "new text");

        verify(postMapper).toDto(post, true, 7, 3);
    }

    @Test
    void deletePost_byNonOwner_throwsForbidden_andNeverDeletes() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(ACCEPTED_FRIEND_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> postService.deletePost(POST_ID));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("You are not authorized to delete this post", ex.getReason());
        verify(postRepository, never()).delete(any());
        verify(notificationService, never()).deleteNotificationsForPost(any());
    }

    @Test
    void deletePost_postNotFound_throwsNotFound_andNeverDeletes() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(SELF_ID).build());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> postService.deletePost(POST_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
        verify(postRepository, never()).delete(any());
    }

    // ---- M-OOP2: notification cleanup now owned by NotificationService; the previously
    // redundant postImageRepository.deleteAll(...) call was removed (Post.images already has
    // cascade=ALL, orphanRemoval=true) ----

    @Test
    void deletePost_byOwner_delegatesNotificationCleanupToService_neverCallsPostImageRepositoryDirectly_stillDeletesPost() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        postService.deletePost(POST_ID);

        verify(notificationService).deleteNotificationsForPost(post);
        verify(postImageRepository, never()).deleteAll(any());
        verify(postRepository).delete(post);
        verifyNoInteractions(cloudinaryService);
    }

    // ---- Cloudinary orphan-image cleanup: deleting a post must also remove every one of its
    // remote images, not just the cascaded DB rows ----

    private PostImage imageOf(String url) {
        PostImage image = new PostImage();
        image.setImageUrl(url);
        return image;
    }

    @Test
    void deletePost_withMultipleImages_deletesEveryRemoteImage_afterTheDbDelete() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        post.setImages(new HashSet<>(List.of(
                imageOf("https://res.cloudinary.com/demo/image/upload/v1/one.png"),
                imageOf("https://res.cloudinary.com/demo/image/upload/v1/two.png")
        )));
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        postService.deletePost(POST_ID);

        verify(cloudinaryService).deleteImage("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        verify(cloudinaryService).deleteImage("https://res.cloudinary.com/demo/image/upload/v1/two.png");
        InOrder order = inOrder(postRepository, cloudinaryService);
        order.verify(postRepository).delete(post);
        order.verify(cloudinaryService, times(2)).deleteImage(any());
    }

    // COR-002 fix: addPost previously saved the post and its image rows via two separate,
    // non-transactional repository calls, and broadcast the new post over WebSocket
    // synchronously - a failure in the image save left an already-committed, imageless post, and
    // any later failure after the broadcast would leave a client having "seen" a post that never
    // fully persisted. Now both writes happen in one transaction and the broadcast is deferred
    // until that transaction actually commits.
    @Test
    void addPost_savesPostAndImages_thenDefersBroadcastUntilAfterCommit() {
        User currentUser = User.builder().id(SELF_ID).build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        Post post = new Post();
        when(postMapper.toDto(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(mock(PostDTO.class));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            postService.addPost(post, List.of("https://res.cloudinary.com/demo/image/upload/v1/one.png"));

            verify(postRepository).save(post);
            verify(postImageRepository).saveAll(any());
            verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));

            List<org.springframework.transaction.support.TransactionSynchronization> syncs =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            syncs.get(0).afterCommit();

            verify(messagingTemplate).convertAndSend(eq("/topic/posts"), any(PostDTO.class));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // COR-001 fix: under a real transaction, the irreversible Cloudinary delete must not run
    // until that transaction has actually committed - never eagerly inside it, where a later
    // commit failure could roll back the DB delete while the remote asset is already gone. The
    // two tests above run with no real Spring transaction manager active, so
    // TransactionSynchronizationManager.isSynchronizationActive() is false and the immediate
    // fallback path runs - this test explicitly activates synchronization (Spring's own
    // supported mechanism for exercising this outside a full transaction manager) to prove the
    // deferred path itself.
    @Test
    void deletePost_underActiveTransaction_defersCloudinaryDeleteUntilAfterCommit() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        post.setImages(new HashSet<>(List.of(imageOf("https://res.cloudinary.com/demo/image/upload/v1/one.png"))));
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            postService.deletePost(POST_ID);

            // DB delete already happened, but the remote delete must not have fired yet.
            verify(postRepository).delete(post);
            verify(cloudinaryService, never()).deleteImage(any());

            List<org.springframework.transaction.support.TransactionSynchronization> syncs =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            // Simulates the transaction manager actually committing.
            syncs.get(0).afterCommit();

            verify(cloudinaryService).deleteImage("https://res.cloudinary.com/demo/image/upload/v1/one.png");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getPostById_postNotFound_throwsNotFound() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(SELF_ID).build());
        when(postRepository.findById(POST_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ResponseStatusException.class, () -> postService.getPostById(POST_ID));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        assertEquals("Post not found", ex.getReason());
    }

    // DBP-003 fix: getPostById's likedByCurrentUser must come from the narrow
    // existsByPost_PostIdAndUser_Id check, not from initializing the post's Like collection.
    @Test
    void getPostById_returnsRealLikedFlagAndCounts() {
        User currentUser = User.builder().id(SELF_ID).build();
        Post post = postOwnedBy(SELF_ID);
        post.setPostId(POST_ID);
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(likeRepository.countLikesByPostId(POST_ID)).thenReturn(5);
        when(commentRepository.countCommentsByPostId(POST_ID)).thenReturn(2);
        when(likeRepository.existsByPost_PostIdAndUser_Id(POST_ID, SELF_ID)).thenReturn(true);

        postService.getPostById(POST_ID);

        verify(postMapper).toDto(post, true, 5, 2);
    }

    // ---- M-DB1/M-DUP1: grouped like/comment counts, shared between getFeedPosts and
    // getPostsByUserDTO, replacing the previous per-post countLikesByPostId/
    // countCommentsByPostId calls inside the mapping loop. ----

    private void loginWithNoFriends() {
        User currentUser = User.builder().id(SELF_ID).build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(friendshipService.getAcceptedFriends(currentUser)).thenReturn(List.of());
    }

    private Post postWithId(long id) {
        Post post = new Post();
        post.setPostId(id);
        return post;
    }

    @Test
    void getFeedPosts_batchesLikeAndCommentCounts_oneGroupedCallNotPerPost() {
        loginWithNoFriends();
        Post postA = postWithId(10L);
        Post postB = postWithId(20L);
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(List.of(postA, postB));
        when(likeRepository.countLikesByPostIds(anyList())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(anyList())).thenReturn(List.of());

        postService.getFeedPosts(0, 10);

        verify(likeRepository, times(1)).countLikesByPostIds(List.of(10L, 20L));
        verify(commentRepository, times(1)).countCommentsByPostIds(List.of(10L, 20L));
        // The old per-post methods must never be invoked - proves no N-sized loop remains.
        verify(likeRepository, never()).countLikesByPostId(any(Long.class));
        verify(commentRepository, never()).countCommentsByPostId(any(Long.class));
    }

    @Test
    void getPostsByUserDTO_batchesLikeAndCommentCounts_oneGroupedCallNotPerPost() {
        User currentUser = User.builder().id(SELF_ID).build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        Post postA = postWithId(30L);
        Post postB = postWithId(40L);
        Post postC = postWithId(50L);
        when(postRepository.findAllPostsWithImagesByUserId(eq((long) SELF_ID), any(Pageable.class)))
                .thenReturn(List.of(postA, postB, postC));
        when(likeRepository.countLikesByPostIds(anyList())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(anyList())).thenReturn(List.of());

        postService.getPostsByUserDTO(SELF_ID, 0, 10);

        verify(likeRepository, times(1)).countLikesByPostIds(List.of(30L, 40L, 50L));
        verify(commentRepository, times(1)).countCommentsByPostIds(List.of(30L, 40L, 50L));
        verify(likeRepository, never()).countLikesByPostId(any(Long.class));
        verify(commentRepository, never()).countCommentsByPostId(any(Long.class));
    }

    @Test
    void toDTOsWithCounts_missingGroupedRows_defaultToZero() {
        loginWithNoFriends();
        Post post = postWithId(99L);
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(List.of(post));
        // No row for postId 99 in either grouped result - simulates a post with zero
        // likes/comments (no matching GROUP BY row is produced for a count of zero).
        when(likeRepository.countLikesByPostIds(anyList())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(anyList())).thenReturn(List.of());

        postService.getFeedPosts(0, 10);

        verify(postMapper).toDto(post, false, 0, 0);
    }

    @Test
    void toDTOsWithCounts_mapsGroupedCountsToCorrectPostIds_notMixedUp() {
        loginWithNoFriends();
        Post postA = postWithId(10L);
        Post postB = postWithId(20L);
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(List.of(postA, postB));
        when(likeRepository.countLikesByPostIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 3L}, new Object[]{20L, 7L}));
        // postA (10L) has no row here at all -> its comment count must be 0, not postB's.
        when(commentRepository.countCommentsByPostIds(anyList()))
                .thenReturn(List.<Object[]>of(new Object[]{20L, 2L}));

        postService.getFeedPosts(0, 10);

        verify(postMapper).toDto(postA, false, 3, 0);
        verify(postMapper).toDto(postB, false, 7, 2);
    }

    // DBP-003 fix: likedByCurrentUser must come from one narrow, bounded query for the whole
    // page's post IDs - never by initializing any post's full Like collection - and each post
    // must get the correct flag, not a page-wide "any post liked" value.
    @Test
    void toDTOsWithCounts_likedByCurrentUser_comesFromOneBoundedQuery_perPostCorrectly() {
        loginWithNoFriends();
        Post likedPost = postWithId(10L);
        Post unlikedPost = postWithId(20L);
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(List.of(likedPost, unlikedPost));
        when(likeRepository.countLikesByPostIds(anyList())).thenReturn(List.of());
        when(commentRepository.countCommentsByPostIds(anyList())).thenReturn(List.of());
        when(likeRepository.findPostIdsLikedByUser(List.of(10L, 20L), SELF_ID)).thenReturn(List.of(10L));

        postService.getFeedPosts(0, 10);

        verify(likeRepository, times(1)).findPostIdsLikedByUser(List.of(10L, 20L), SELF_ID);
        verify(postMapper).toDto(likedPost, true, 0, 0);
        verify(postMapper).toDto(unlikedPost, false, 0, 0);
    }

    @Test
    void getFeedPosts_emptyResult_neverCallsCountRepositories() {
        loginWithNoFriends();
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<PostDTO> result = postService.getFeedPosts(0, 10);

        assertTrue(result.isEmpty());
        verifyNoInteractions(likeRepository);
        verifyNoInteractions(commentRepository);
        verifyNoInteractions(postMapper);
    }

    @Test
    void getPostsByUserDTO_emptyResult_neverCallsCountRepositories() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(SELF_ID).build());
        when(postRepository.findAllPostsWithImagesByUserId(anyLong(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<PostDTO> result = postService.getPostsByUserDTO(SELF_ID, 0, 10);

        assertTrue(result.isEmpty());
        verifyNoInteractions(likeRepository);
        verifyNoInteractions(commentRepository);
        verifyNoInteractions(postMapper);
    }

    // ---- DBP-002 fix: page/size are clamped to a safe range instead of passed straight into
    // PageRequest.of, which previously threw on a negative page and drove an unbounded query on
    // an arbitrarily large size. ----

    @Test
    void getFeedPosts_negativePage_clampedToZero_andOversizedSize_clampedToMax() {
        loginWithNoFriends();
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getFeedPosts(-5, 1_000_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAllPostsWithImages(anyList(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(PostService.MAX_FEED_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getFeedPosts_zeroOrNegativeSize_fallsBackToDefault() {
        loginWithNoFriends();
        when(postRepository.findAllPostsWithImages(anyList(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getFeedPosts(0, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAllPostsWithImages(anyList(), pageableCaptor.capture());
        assertEquals(PostService.DEFAULT_FEED_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getPostsByUserDTO_negativePage_clampedToZero_andOversizedSize_clampedToMax() {
        when(authHelper.getCurrentUser()).thenReturn(User.builder().id(SELF_ID).build());
        when(postRepository.findAllPostsWithImagesByUserId(anyLong(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getPostsByUserDTO(SELF_ID, -5, 1_000_000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findAllPostsWithImagesByUserId(anyLong(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(PostService.MAX_WALL_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }
}

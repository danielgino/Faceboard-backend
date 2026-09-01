package org.example.apimywebsite.service;

import org.springframework.transaction.annotation.Transactional;
import org.example.apimywebsite.api.model.*;
import org.example.apimywebsite.dto.PostDTO;
import org.example.apimywebsite.mapper.PostMapper;
import org.example.apimywebsite.repository.*;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.example.apimywebsite.util.TransactionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final  CommentRepository commentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PostImageRepository postImageRepository;
    private final AuthHelper authHelper;
    private final NotificationService notificationService;
    private final PostMapper postMapper;
    private final FriendshipService friendshipService;
    private final CloudinaryService cloudinaryService;

    // M-OOP2: NotificationRepository replaced with NotificationService (deleteNotificationsForPost)
    // - notification cleanup is a cross-domain write that NotificationService now owns; no
    // circular dependency, since NotificationService does not depend on PostService.
    @Autowired
    public PostService(PostRepository postRepository,LikeRepository likeRepository,CommentRepository commentRepository,SimpMessagingTemplate messagingTemplate,PostImageRepository postImageRepository,AuthHelper authHelper,NotificationService notificationService,PostMapper postMapper,FriendshipService friendshipService,CloudinaryService cloudinaryService) {
        this.postRepository = postRepository;
        this.likeRepository=likeRepository;
        this.commentRepository=commentRepository;
        this.messagingTemplate=messagingTemplate;
        this.postImageRepository=postImageRepository;
        this.authHelper=authHelper;
        this.notificationService=notificationService;
        this.postMapper=postMapper;
        this.friendshipService=friendshipService;
        this.cloudinaryService=cloudinaryService;
    }

// COR-002 fix: the post row and its image rows were saved via two separate, individually
// auto-committing repository calls with no shared transaction - a failure in the second
// (postImageRepository.saveAll) left a permanently empty, imageless post already committed from
// the first. @Transactional makes both writes atomic: either the post and all its images persist
// together, or neither does. The WebSocket broadcast is deferred to after commit (via
// TransactionUtils, same fix as COR-001/other COR-002 items) so a client can never be notified
// of a post whose transaction went on to roll back.
@Transactional
public PostDTO addPost(Post post, List<String> imageUrls) {
    User user = authHelper.getCurrentUser();
    post.setUser(user);
    post.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    postRepository.save(post);
    List<PostImage> images = imageUrls.stream()
            .map(url -> {
                PostImage image = new PostImage();
                image.setPost(post);
                image.setImageUrl(url);
                return image;
            })
            .toList();
    postImageRepository.saveAll(images);
    post.setImages(new HashSet<>(images));
    PostDTO postDTO = postMapper.toDto(post, false, 0, 0); // brand new post: no likes possible yet
    TransactionUtils.runAfterCommit(() -> messagingTemplate.convertAndSend("/topic/posts", postDTO));
    return postDTO;
}

    // H-PAGE1: readOnly=true keeps a Hibernate session/persistence-context open for the whole
    // method body - independent of spring.jpa.open-in-view - so the lazy-loaded, @BatchSize
    // collections (Post.images, Post.likes) that toDTOsWithCounts/PostMapper touch below are
    // initialized here, inside the transaction, and never leak an entity with an already-closed
    // session out to the controller. Only PostDTOs (plain data, no proxies) cross that boundary.
    // DBP-002 fix: page/size previously passed straight into PageRequest.of, so a negative page
    // threw and an arbitrarily large size drove an unbounded query/mapping load. Clamped the same
    // way MessageService already clamps conversation paging - out-of-range values fall back to a
    // safe default/max rather than erroring, so existing callers that only ever send sane values
    // see no behavior change.
    public static final int DEFAULT_FEED_PAGE_SIZE = 5;
    public static final int MAX_FEED_PAGE_SIZE = 50;
    public static final int DEFAULT_WALL_PAGE_SIZE = 10;
    public static final int MAX_WALL_PAGE_SIZE = 50;

    private static Pageable safePageable(int page, int size, int defaultSize, int maxSize) {
        int safePage = Math.max(page, 0);
        int safeSize = (size <= 0) ? defaultSize : Math.min(size, maxSize);
        return PageRequest.of(safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<PostDTO> getFeedPosts( int page, int size) {
        User currentUser = authHelper.getCurrentUser();
        List<Integer> userIds = new ArrayList<>();
        userIds.add(currentUser.getId());
        userIds.addAll(
                friendshipService.getAcceptedFriends(currentUser).stream()
                        .map(User::getId)
                        .toList()
        );
        Pageable pageable = safePageable(page, size, DEFAULT_FEED_PAGE_SIZE, MAX_FEED_PAGE_SIZE);
        List<Post> posts = postRepository.findAllPostsWithImages(userIds, pageable);
        return toDTOsWithCounts(posts, currentUser.getId());
    }



    // H-PAGE1: readOnly=true keeps the session open for post.getImages() below (LAZY +
    // @BatchSize(16) on Post.images - one batched "WHERE post_id IN (...)" query per 16 posts,
    // not per-post), independent of spring.jpa.open-in-view. Only plain Strings cross out of the
    // transactional boundary.
    @Transactional(readOnly = true)
    public List<String> getAllPostImageUrlsByUserId(int userId) {
        List<Post> posts = getPostsByUserId(userId);
        return posts.stream()
                .flatMap(post -> post.getImages().stream())
                .map(PostImage::getImageUrl)
                .collect(Collectors.toList());
    }
    // H-PAGE1: see getFeedPosts - same readOnly-transaction rationale, applied to the
    // profile-wall listing.
    @Transactional(readOnly = true)
    public List<PostDTO> getPostsByUserDTO(long userId, int page, int size) {
        User currentUser = authHelper.getCurrentUser();
        Pageable pageable = safePageable(page, size, DEFAULT_WALL_PAGE_SIZE, MAX_WALL_PAGE_SIZE);
        List<Post> posts = postRepository.findAllPostsWithImagesByUserId(userId, pageable);
        return toDTOsWithCounts(posts, currentUser.getId());
    }

    // M-DB1/M-DUP1: shared enrichment path for every paginated post-listing endpoint (feed,
    // wall/profile). Replaces the previous per-post countLikesByPostId/countCommentsByPostId
    // calls (2 extra queries per post in the page) with exactly 2 grouped queries total,
    // regardless of page size. likedByCurrentUser and every other PostDTO field are produced by
    // the same, unmodified PostMapper.toDto - only how likeCount/commentCount are sourced
    // changed.
    private List<PostDTO> toDTOsWithCounts(List<Post> posts, int currentUserId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = posts.stream().map(Post::getPostId).toList();
        Map<Long, Integer> likeCounts = toCountMap(likeRepository.countLikesByPostIds(postIds));
        Map<Long, Integer> commentCounts = toCountMap(commentRepository.countCommentsByPostIds(postIds));
        // DBP-003 fix: one narrow, bounded query for the whole page's "did the current user like
        // this one" flags, instead of PostMapper initializing every post's full Like collection.
        Set<Long> likedPostIds = new HashSet<>(likeRepository.findPostIdsLikedByUser(postIds, currentUserId));
        return posts.stream()
                .map(post -> postMapper.toDto(
                        post,
                        likedPostIds.contains(post.getPostId()),
                        likeCounts.getOrDefault(post.getPostId(), 0),
                        commentCounts.getOrDefault(post.getPostId(), 0)))
                .toList();
    }

    private Map<Long, Integer> toCountMap(List<Object[]> groupedCountRows) {
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : groupedCountRows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    @Transactional
    public PostDTO editPost(long postId, String newContent) {
        User currentUser = authHelper.getCurrentUser();

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        if (post.getUser().getId() != currentUser.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to edit this post");
        }
        post.setPostText(newContent);
        post.setEdited(true);
        postRepository.save(post);

        int likeCount = likeRepository.countLikesByPostId(post.getPostId());
        int commentCount = commentRepository.countCommentsByPostId(post.getPostId());
        boolean likedByCurrentUser = likeRepository.existsByPost_PostIdAndUser_Id(post.getPostId(), currentUser.getId());
        return postMapper.toDto(post, likedByCurrentUser, likeCount, commentCount);

    }

    public List<Post> getPostsByUserId(long userId) {
        return postRepository.findByUserId(userId);
    }


    @Transactional
    public void deletePost(long postId) {
        User currentUser = authHelper.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        if (post.getUser().getId() != currentUser.getId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to delete this post");
        }
        // M-OOP2: notification cleanup now owned by NotificationService (no JPA cascade exists
        // for Notification.post, unlike likes/comments/images below). The explicit
        // postImageRepository.deleteAll(...) call that used to be here was removed as redundant:
        // Post.images is @OneToMany(cascade=CascadeType.ALL, orphanRemoval=true), so
        // postRepository.delete(post) below already cascade-deletes every PostImage row (and
        // likewise for likes/comments, which were never explicitly deleted here to begin with).
        // Cloudinary orphan-image cleanup: cascade only removes the PostImage DB rows, never the
        // remote files - captured before delete() (Post.images is LAZY; still loadable here,
        // inside the active transaction) so every image's Cloudinary asset can be removed too,
        // best-effort, after the DB delete.
        List<String> imageUrls = post.getImages().stream().map(PostImage::getImageUrl).toList();
        notificationService.deleteNotificationsForPost(post);

        postRepository.delete(post);

        // COR-001 fix: deletion used to run here, inside this still-open transaction. Cloudinary
        // deletion is irreversible and cannot participate in the DB transaction - if the commit
        // that follows this method returning ever failed (deadlock, constraint error, connection
        // loss), the DB delete would roll back while the images were already gone for good.
        // Deferring to afterCommit() means the remote assets are only ever removed once the DB
        // delete has actually, durably committed. CloudinaryService.deleteImage is already
        // defensive/idempotent (catches and logs internally, safe to call on an
        // already-deleted/missing asset), and Spring logs rather than propagates exceptions from
        // afterCommit callbacks, so a Cloudinary failure here can never turn an already-successful
        // delete into an error response.
        TransactionUtils.runAfterCommit(() -> imageUrls.forEach(cloudinaryService::deleteImage));
    }
    @Transactional
    public PostDTO getPostById(long postId) {
        User currentUser = authHelper.getCurrentUser();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));

        int likeCount = likeRepository.countLikesByPostId(post.getPostId());
        int commentCount = commentRepository.countCommentsByPostId(post.getPostId());
        boolean likedByCurrentUser = likeRepository.existsByPost_PostIdAndUser_Id(post.getPostId(), currentUser.getId());

        return postMapper.toDto(post, likedByCurrentUser, likeCount, commentCount);
    }
}


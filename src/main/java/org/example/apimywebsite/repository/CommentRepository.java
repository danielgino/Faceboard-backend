package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Demo Mode: used by DemoDataSeederService to clean up a partially-seeded user's comments
    // wherever they are - including ones left on another (still-present) seed user's post,
    // which deleting only this user's own posts would never reach. A bulk JPQL delete (not the
    // derived query-then-remove-each form) so it executes immediately and clearAutomatically
    // evicts any already-loaded Comment entities from the persistence context - otherwise a
    // later flush's FK/cascade check can still see stale references to rows that are already
    // gone from the database.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.user = :user")
    void deleteAllByUser(@Param("user") User user);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.postId = :postId")
    int countCommentsByPostId(@Param("postId") Long postId);

    // M-DB1: one grouped query for an entire page of posts, instead of one
    // countCommentsByPostId call per post. Post IDs with zero comments simply have no row here -
    // callers must default missing IDs to 0.
    @Query("SELECT c.post.postId AS postId, COUNT(c) AS cnt FROM Comment c WHERE c.post.postId IN :postIds GROUP BY c.post.postId")
    List<Object[]> countCommentsByPostIds(@Param("postIds") List<Long> postIds);

    // L-DB4C: bounded, page-based read instead of the previous unbounded fetch. Ordered newest
    // first (with commentId as a deterministic tie-breaker for same-instant comments) so page 0
    // is always the most recent N comments; CommentService reverses each page to ASC before
    // returning, preserving the external chronological contract. Callers must pass an unsorted
    // PageRequest.of(page, size) so Pageable contributes no competing Sort.
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.postId = :postId ORDER BY c.createdAt DESC, c.commentId DESC")
    List<Comment> findCommentsByPostIdWithUser(@Param("postId") Long postId, Pageable pageable);

}
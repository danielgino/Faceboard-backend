package org.example.apimywebsite.repository;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {
        void deleteByPost_PostIdAndUser_Id(long postId, long userId);

        // Demo Mode: used by DemoDataSeederService to clean up a partially-seeded user's likes
        // wherever they are - including ones left on another (still-present) seed user's post,
        // which deleting only this user's own posts would never reach. A bulk JPQL delete (not
        // the derived query-then-remove-each form) so it executes immediately and
        // clearAutomatically evicts any already-loaded Like entities from the persistence
        // context - otherwise a later flush's FK/cascade check can still see stale references
        // to rows that are already gone from the database.
        @Modifying(clearAutomatically = true)
        @Query("DELETE FROM Like l WHERE l.user = :user")
        void deleteAllByUser(@Param("user") User user);

        boolean existsByPost_PostIdAndUser_Id(Long postId, int userid);
        long countByPost(Post post);

        // L-DB4C: bounded, page-based read instead of the previous unbounded fetch. Ordered by
        // createdAt DESC with likeId as a deterministic tie-breaker; callers must pass an unsorted
        // PageRequest.of(page, size) so Pageable contributes no competing Sort.
        @Query("SELECT l FROM Like l JOIN FETCH l.user WHERE l.post.postId = :postId ORDER BY l.createdAt DESC, l.likeId DESC")
        List<Like> findByPostIdWithUser(@Param("postId") Long postId, Pageable pageable);

        @Query("SELECT COUNT(l) FROM Like l WHERE l.post.postId = :postId")
        int countLikesByPostId(@Param("postId") Long postId);

        // M-DB1: one grouped query for an entire page of posts, instead of one
        // countLikesByPostId call per post. Post IDs with zero likes simply have no row here -
        // callers must default missing IDs to 0.
        @Query("SELECT l.post.postId AS postId, COUNT(l) AS cnt FROM Like l WHERE l.post.postId IN :postIds GROUP BY l.post.postId")
        List<Object[]> countLikesByPostIds(@Param("postIds") List<Long> postIds);

        // DBP-003 fix: narrow projection - just the post IDs (among the given page) the current
        // user has liked - instead of initializing every Like entity on Post.likes just to
        // compute one boolean per post. One bounded query per page, regardless of how many likes
        // any of those posts actually have.
        @Query("SELECT l.post.postId FROM Like l WHERE l.post.postId IN :postIds AND l.user.id = :userId")
        List<Long> findPostIdsLikedByUser(@Param("postIds") List<Long> postIds, @Param("userId") int userId);

}

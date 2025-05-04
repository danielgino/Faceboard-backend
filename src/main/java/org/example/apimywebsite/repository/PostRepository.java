package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;

import org.example.apimywebsite.api.model.PostImage;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;


import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
    SELECT DISTINCT p FROM Post p
    JOIN FETCH p.user
    LEFT JOIN FETCH p.images
    LEFT JOIN FETCH p.comments
    LEFT JOIN FETCH p.likes
    WHERE p.user.id IN :userIds
    ORDER BY p.createdAt DESC
""")
    List<Post> findFullFeedPosts(@Param("userIds") List<Integer> userIds, Pageable pageable);
    @Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.user LEFT JOIN FETCH p.images WHERE p.user.id IN :userIds ORDER BY p.createdAt DESC")
    List<Post> findPostsWithImagesByUserIds(@Param("userIds") List<Integer> userIds, Pageable pageable);

    @Query("SELECT i FROM PostImage i WHERE i.post.postId IN :postIds")
    List<PostImage> findImagesByPostIds(@Param("postIds") List<Long> postIds);

    @Query("SELECT c FROM Comment c WHERE c.post.postId IN :postIds")
    List<Comment> findCommentsByPostIds(@Param("postIds") List<Long> postIds);

    @Query("SELECT DISTINCT p FROM Post p " +
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH p.user " +
            "WHERE p.user.id = :userId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImagesByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.comments WHERE p.id IN :postIds")
    List<Post> fetchCommentsForPosts(@Param("postIds") List<Long> postIds);
    @Query("""
    SELECT p FROM Post p
    WHERE p.user.id IN :userIds
    ORDER BY p.createdAt DESC
""")
    List<Post> findPostsByUserIds(@Param("userIds") List<Integer> userIds, Pageable pageable);
    @Query("""
SELECT DISTINCT p FROM Post p
LEFT JOIN FETCH p.images
LEFT JOIN FETCH p.comments
LEFT JOIN FETCH p.likes
JOIN FETCH p.user
WHERE p.user.id = :userId
ORDER BY p.createdAt DESC
""")
    List<Post> findFullPostsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images WHERE p.user.id IN :userIds ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImages(@Param("userIds") List<Integer> userIds, Pageable pageable);
    @Query("SELECT DISTINCT p FROM Post p " +
            "LEFT JOIN FETCH p.images " +
            "LEFT JOIN FETCH p.comments " +
            "LEFT JOIN FETCH p.user " +
            "WHERE p.user.id = :userId " +
            "ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImagesAndCommentsByUserId(@Param("userId") Long userId, Pageable pageable);
    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImagesByUserId(@Param("userId") Long userId, Pageable pageable);

    List<Post> findByUserId(long userId);

    Post findByPostId(long Id);

}

package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Comment;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c WHERE c.post.postId IN :postIds")
    List<Comment> findByPostIds(@Param("postIds") List<Long> postIds);

    @Query("SELECT COUNT(c) FROM Comment c WHERE c.post.postId = :postId")
    int countCommentsByPostId(@Param("postId") Long postId);
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.postId = :postId ORDER BY c.createdAt ASC")
    List<Comment> findCommentsByPostIdWithUser(@Param("postId") Long postId);

}
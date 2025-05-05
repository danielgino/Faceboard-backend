package org.example.apimywebsite.repository;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {
        void deleteByPost_PostIdAndUser_Id(long postId, long userId);

        boolean existsByPost_PostIdAndUser_Id(Long postId, int userid);
        long countByPost(Post post);
        @Query("SELECT l FROM Like l JOIN FETCH l.user WHERE l.post.postId = :postId")
        List<Like> findByPostIdWithUser(@Param("postId") Long postId);

        @Query("SELECT COUNT(l) FROM Like l WHERE l.post.postId = :postId")
        int countLikesByPostId(@Param("postId") Long postId);




}

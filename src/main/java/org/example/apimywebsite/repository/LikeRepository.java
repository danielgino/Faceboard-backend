package org.example.apimywebsite.repository;
import org.example.apimywebsite.api.model.Like;
import org.example.apimywebsite.api.model.Post;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {
        void deleteByPost_PostIdAndUser_Id(long postId, long userId);

        boolean existsByPost_PostIdAndUser_Id(Long postId, int userid);
        long countByPost(Post post);



}

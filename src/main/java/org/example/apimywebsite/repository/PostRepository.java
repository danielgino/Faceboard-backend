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

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images WHERE p.user.id IN :userIds ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImages(@Param("userIds") List<Integer> userIds, Pageable pageable);

    @Query("SELECT DISTINCT p FROM Post p LEFT JOIN FETCH p.images WHERE p.user.id = :userId ORDER BY p.createdAt DESC")
    List<Post> findAllPostsWithImagesByUserId(@Param("userId") Long userId, Pageable pageable);

    List<Post> findByUserId(long userId);

    Post findByPostId(long Id);

}

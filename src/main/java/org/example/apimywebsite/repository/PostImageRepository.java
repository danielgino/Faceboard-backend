package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage,Long> {

    @Query("SELECT i FROM PostImage i WHERE i.post.postId IN :postIds")
    List<PostImage> findByPostIds(@Param("postIds") List<Long> postIds);

}

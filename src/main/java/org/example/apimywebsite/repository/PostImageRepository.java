package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage,Long> {

    @Query("SELECT pi.imageUrl FROM PostImage pi WHERE pi.post.user.id = :userId")
    List<String> findImageUrlsByUserId(@Param("userId") int userId);

}

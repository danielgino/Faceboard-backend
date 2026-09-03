package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage,Long> {

    List<PostImage> findByPost_PostId(long postId);
}

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

    // H-PAGE1 (HHH90003004 fix): images is a @OneToMany collection, so a JOIN FETCH here would
    // fan the result out to one row per image, making a real SQL LIMIT/OFFSET impossible -
    // Hibernate would instead load every matching post into memory and paginate in Java
    // (HHH90003004; fails outright with hibernate.query.fail_on_pagination_over_collection_fetch
    // enabled). Selecting the bare Post lets Pageable apply as a genuine LIMIT/OFFSET. Images are
    // then loaded lazily via Post.images' existing FetchType.LAZY + @BatchSize(16) (Post.java) -
    // one extra "WHERE post_id IN (...)" query for the whole page, not N+1 - exactly the pattern
    // already used for Post.likes (see PostMapper.toDto's likedByCurrentUser). Both batch loads
    // happen inside PostService's @Transactional(readOnly = true) methods (getFeedPosts /
    // getPostsByUserDTO), not via spring.jpa.open-in-view, so lazy loading keeps working even
    // with OSIV disabled. postId DESC is a deterministic tiebreaker for posts sharing the same
    // createdAt instant, matching the tiebreaker convention already used in
    // LikeRepository/CommentRepository.
    @Query("SELECT p FROM Post p WHERE p.user.id IN :userIds ORDER BY p.createdAt DESC, p.postId DESC")
    List<Post> findAllPostsWithImages(@Param("userIds") List<Integer> userIds, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.user.id = :userId ORDER BY p.createdAt DESC, p.postId DESC")
    List<Post> findAllPostsWithImagesByUserId(@Param("userId") Long userId, Pageable pageable);

    List<Post> findByUserId(long userId);

    Post findByPostId(long Id);

}

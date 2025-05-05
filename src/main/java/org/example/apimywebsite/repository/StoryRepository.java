package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

//    List<Story> findByUserIdInAndExpiresAtAfter(List<Integer> userIds, LocalDateTime now);
    @Query("SELECT s FROM Story s WHERE s.user.id IN :userIds AND s.expiresAt > :now")
    List<Story> findByUserIdInAndExpiresAtAfter(@Param("userIds") List<Integer> userIds,
                                                @Param("now") LocalDateTime now);

//    List<Story> findByExpiresAtBefore(LocalDateTime now);
@Query("SELECT s FROM Story s WHERE s.expiresAt < :now")
List<Story> findByExpiresAtBefore(@Param("now") LocalDateTime now);
}

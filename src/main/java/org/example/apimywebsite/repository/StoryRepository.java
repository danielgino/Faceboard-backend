package org.example.apimywebsite.repository;


import org.example.apimywebsite.api.model.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByUserIdInAndExpiresAtAfter(List<Integer> userIds, LocalDateTime now);

    List<Story> findByExpiresAtBefore(LocalDateTime now);
}

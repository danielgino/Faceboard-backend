package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // L-DB4B: Pageable applied as real LIMIT/OFFSET at the SQL level. Ordering stays expressed
    // once, via the derived method name (OrderByCreatedAtDesc) - callers must pass an unsorted
    // PageRequest.of(page, size) so Pageable contributes no competing Sort, avoiding a
    // duplicate/conflicting order definition.
    // Sender is fetched eagerly here (query-specific, not entity-level) because mapToDTO()
    // reads sender.getFullName() after the session closes; receiver stays lazy since it's unused.
    @EntityGraph(attributePaths = "sender")
    List<Notification> findByReceiverIdOrderByCreatedAtDesc(Integer receiverId, Pageable pageable);

    List<Notification> findByReceiverIdAndReadFalseOrderByCreatedAtDesc(Integer receiverId);

    Long countByReceiverIdAndReadFalse(Integer receiverId);

    // COR-009 fix: the previous derived `deleteByCreatedAtBefore` is executed by Spring Data JPA
    // as a SELECT of every matching row followed by one EntityManager.remove() per entity - for
    // 30-day retention cleanup that can mean loading and individually deleting a large, ever-
    // growing batch of old notifications. One bulk JPQL DELETE is a single SQL statement
    // regardless of how many rows match, and returning the affected-row count lets the caller log
    // what actually happened instead of running blind.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);

    void deleteByPost(Post post);


}
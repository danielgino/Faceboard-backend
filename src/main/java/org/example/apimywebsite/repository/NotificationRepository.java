package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Notification;
import org.example.apimywebsite.api.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByReceiverIdOrderByCreatedAtDesc(Integer receiverId);

    List<Notification> findByReceiverIdAndReadFalseOrderByCreatedAtDesc(Integer receiverId);

    Long countByReceiverIdAndReadFalse(Integer receiverId);
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
    void deleteByPost(Post post);


}
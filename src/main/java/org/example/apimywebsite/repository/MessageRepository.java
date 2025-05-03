package org.example.apimywebsite.repository;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Integer> {


    @Query("""
    SELECT m FROM Message m
    WHERE (m.sender.id = :userId AND m.receiver.id = :friendId)
       OR (m.sender.id = :friendId AND m.receiver.id = :userId)
    ORDER BY m.sentTime DESC
""")
    List<Message> findMessagesBetweenUsers(int userId, int friendId, org.springframework.data.domain.Pageable pageable);
    @Query("""
    SELECT m FROM Message m
    WHERE (m.sender.id = :userId AND m.receiver.id = :otherUserId)
       OR (m.sender.id = :otherUserId AND m.receiver.id = :userId)
    ORDER BY m.sentTime ASC
""")
    List<Message> findConversationBetweenUsers(int userId, int otherUserId);
    List<Message> findBySenderIdAndReceiverIdAndIsReadFalse(int senderId, int receiverId);

}

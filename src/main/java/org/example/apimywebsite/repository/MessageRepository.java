package org.example.apimywebsite.repository;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Message;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("""
    SELECT m.sender.id AS senderId, COUNT(m) AS cnt
    FROM Message m
    WHERE m.receiver.id = :userId AND m.isRead = false
    GROUP BY m.sender.id
""")
    List<Object[]> countUnreadBySenderGrouped(@Param("userId") int userId);

    @Query("""
    SELECT m FROM Message m
    WHERE (m.sender.id = :userId AND m.receiver.id IN :friendIds)
       OR (m.receiver.id = :userId AND m.sender.id IN :friendIds)
    AND m.sentTime IN (
        SELECT MAX(m2.sentTime) FROM Message m2
        WHERE 
            ((m2.sender.id = :userId AND m2.receiver.id IN :friendIds)
             OR (m2.receiver.id = :userId AND m2.sender.id IN :friendIds))
        GROUP BY 
            CASE 
                WHEN m2.sender.id < m2.receiver.id THEN CONCAT(m2.sender.id, '-', m2.receiver.id)
                ELSE CONCAT(m2.receiver.id, '-', m2.sender.id)
            END
    )
""")
    List<Message> findLastMessagesBetweenUserAndFriends(@Param("userId") int userId, @Param("friendIds") List<Integer> friendIds);

}

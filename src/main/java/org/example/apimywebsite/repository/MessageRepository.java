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
    List<Message> findBySenderIdAndReceiverIdAndIsReadFalse(int senderId, int receiverId);

    @Query("""
    SELECT m.sender.id AS senderId, COUNT(m) AS cnt
    FROM Message m
    WHERE m.receiver.id = :userId AND m.isRead = false
    GROUP BY m.sender.id
""")
    List<Object[]> countUnreadBySenderGrouped(@Param("userId") int userId);

    // M-DB3: for each candidate message m between userId and one of friendIds, return m only if
    // no strictly-newer message exists between that same unordered pair (NOT EXISTS anti-join).
    // "Strictly newer" = later sentTime, or an equal sentTime broken deterministically by the
    // higher (later-generated, GenerationType.IDENTITY / auto-increment) id - see Message.id.
    // This returns exactly one row per pair: whichever message has no newer message beats it.
    // Fixes two defects in the prior version: (1) a boolean-operator-precedence bug where
    // "A OR B AND C" parsed as "A OR (B AND C)" instead of the intended "(A OR B) AND C", so the
    // max-sentTime constraint silently never applied to messages the user themselves sent -
    // every explicit parenthesization below exists to prevent that class of bug; (2) a
    // non-sargable GROUP BY on a computed CASE/CONCAT string key, replaced here with direct
    // column comparisons only (sender.id/receiver.id/sentTime/id), no computed grouping key.
    @Query("""
    SELECT m FROM Message m
    WHERE (
            (m.sender.id = :userId AND m.receiver.id IN :friendIds)
            OR (m.receiver.id = :userId AND m.sender.id IN :friendIds)
          )
      AND NOT EXISTS (
            SELECT m2 FROM Message m2
            WHERE (
                    (m2.sender.id = m.sender.id AND m2.receiver.id = m.receiver.id)
                    OR (m2.sender.id = m.receiver.id AND m2.receiver.id = m.sender.id)
                  )
              AND (
                    m2.sentTime > m.sentTime
                    OR (m2.sentTime = m.sentTime AND m2.id > m.id)
                  )
          )
""")
    List<Message> findLastMessagesBetweenUserAndFriends(@Param("userId") int userId, @Param("friendIds") List<Integer> friendIds);

}

package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friends, FriendshipId> {

    Optional<Friends> findByUserAndFriend(User user, User friend);

    List<Friends> findAllByUserAndStatus(User user, FriendshipStatus status);

    @Query("""
        SELECT f FROM Friends f
        WHERE f.user = :user AND f.status = :status
          AND (:query IS NULL OR :query = ''
            OR LOWER(f.friend.name) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(f.friend.lastname) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(CONCAT(f.friend.name, ' ', f.friend.lastname)) LIKE LOWER(CONCAT('%', :query, '%'))
            OR LOWER(f.friend.userName) LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY f.createdAt DESC, f.friend.id DESC
    """)
    List<Friends> searchAcceptedFriendsPage(@Param("user") User user, @Param("status") FriendshipStatus status, @Param("query") String query, Pageable pageable);

}

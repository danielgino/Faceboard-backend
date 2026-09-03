package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.enums.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friends, FriendshipId> {

    Optional<Friends> findByUserAndFriend(User user, User friend);

    List<Friends> findAllByUserAndStatus(User user, FriendshipStatus status);

    // Demo Mode: used by DemoDataSeederService to clean up a partially-seeded user's friendship
    // rows (either direction) before re-seeding, inside the same transaction. clearAutomatically
    // is required, not optional: a bulk JPQL DELETE bypasses the persistence context entirely,
    // so without it any already-loaded Friends entities stay in the L1 cache as stale
    // references - Hibernate then finds them during the next flush's cascade/transience check
    // (e.g. the subsequent userRepository.delete(user) below) and throws, even though the rows
    // are already gone from the database.
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Friends f WHERE f.user = :user OR f.friend = :user")
    void deleteAllInvolvingUser(@Param("user") User user);

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

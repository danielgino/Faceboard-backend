package org.example.apimywebsite.repository;

import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.api.model.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friends, Long> {
    boolean existsById(FriendshipId friendshipId);

    Optional<Friends> findByUserAndFriend(User user, User friend);

    List<Friends> findAllByUserAndStatus(User user, FriendshipStatus status);

    List<Friends> findAllByFriendAndStatus(User user, FriendshipStatus status);
}

package org.example.apimywebsite.service;

import jakarta.transaction.Transactional;
import org.example.apimywebsite.api.model.Friends;
import org.example.apimywebsite.api.model.FriendshipId;
import org.example.apimywebsite.api.model.FriendshipStatus;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.FriendRequestDTO;
import org.example.apimywebsite.repository.FriendshipRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FriendshipService {


    private final FriendshipRepository friendshipRepository;

    private final NotificationService notificationService;

    public FriendshipService(FriendshipRepository friendshipRepository, @Lazy NotificationService notificationService) {
        this.friendshipRepository = friendshipRepository;
        this.notificationService = notificationService;
    }

    public boolean sendFriendRequest(User user, User friend) {
        FriendshipId friendshipId = new FriendshipId(user.getId(), friend.getId());
        if (friendshipRepository.existsById(friendshipId)) {
            return false;
        }
        String content = user.getFullName() + " Send you a Friend Request";
          notificationService.createNotification(friend, user, "FRIEND_REQUEST", content, null);
        Friends friends = new Friends(user, friend, FriendshipStatus.PENDING, LocalDateTime.now());
        friends.setId(friendshipId);


        friendshipRepository.save(friends);
        return true;
    }


    @Transactional
    public Friends acceptFriendRequest(User user, User friend) {
        Friends existingRequest = friendshipRepository.findByUserAndFriend(friend, user)
                .orElse(null);

        if (existingRequest == null || !existingRequest.getStatus().equals(FriendshipStatus.PENDING)) {
            throw new IllegalArgumentException("Friend request not found or already processed.");
        }
        existingRequest.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(existingRequest);


        FriendshipId reciprocalId = new FriendshipId(user.getId(), friend.getId());
        if (!friendshipRepository.existsById(reciprocalId)) {
            Friends reciprocalFriendship = new Friends(user, friend, FriendshipStatus.ACCEPTED, LocalDateTime.now());
            friendshipRepository.save(reciprocalFriendship);
        }
        String content = user.getFullName() + " Accept You Friend Request";
        notificationService.createNotification(friend, user, "FRIEND_ACCEPTED", content, null);

        return existingRequest;
    }


    @Transactional
    public void declineFriendRequest(User receiver, User sender) {
        Friends request = friendshipRepository.findByUserAndFriend(sender, receiver)
                .orElseThrow(() -> new IllegalArgumentException("Friend request not found."));

        if (request.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalStateException("Cannot decline a non-pending request.");
        }

        friendshipRepository.delete(request);

    }
    public FriendRequestDTO getRelationshipBetweenUsers(User user, User friend) {
        Friends outgoingRequest = friendshipRepository.findByUserAndFriend(user, friend).orElse(null);
        if (outgoingRequest != null) {
            return new FriendRequestDTO(user.getId(), friend.getId(), outgoingRequest.getStatus().name());
        }
        Friends incomingRequest = friendshipRepository.findByUserAndFriend(friend, user).orElse(null);

        if (incomingRequest != null) {
            return new FriendRequestDTO(friend.getId(), user.getId(), incomingRequest.getStatus().name());
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No friendship found between users");
    }

    public List<User> getAcceptedFriends(User user) {
        return friendshipRepository.findAllByUserAndStatus(user, FriendshipStatus.ACCEPTED)
                .stream()
                .map(Friends::getFriend)
                .toList();
    }

    public List<User> getSentFriendRequests(User user) {
        return friendshipRepository.findAllByUserAndStatus(user, FriendshipStatus.PENDING)
                .stream()
                .map(Friends::getFriend)
                .toList();
    }

    public List<User> getReceivedFriendRequests(User user) {
        return friendshipRepository.findAllByFriendAndStatus(user, FriendshipStatus.PENDING)
                .stream()
                .map(Friends::getUser)
                .toList();
    }
    public void removeFriend(User user, User friend) {
        Optional<Friends> friendship = friendshipRepository.findByUserAndFriend(user, friend);
        friendship.ifPresent(friendshipRepository::delete);
    }
}

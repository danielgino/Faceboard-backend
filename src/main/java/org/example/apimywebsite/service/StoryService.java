package org.example.apimywebsite.service;


import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class StoryService {

    private final StoryRepository storyRepository;
    private final FriendshipService friendshipService;
    private final CloudinaryService cloudinaryService;

    public StoryService(StoryRepository storyRepository, FriendshipService friendshipService,CloudinaryService cloudinaryService) {
        this.storyRepository = storyRepository;
        this.friendshipService = friendshipService;
        this.cloudinaryService=cloudinaryService;
    }

    public List<Story> getVisibleStories(User user) {
        List<User> friends = friendshipService.getAcceptedFriends(user);
        List<Integer> friendIds = friends.stream()
                .map(User::getId)
                .collect(Collectors.toList());
        friendIds.add(user.getId());

        return storyRepository.findByUserIdInAndExpiresAtAfter(friendIds, LocalDateTime.now());
    }


    public Story uploadStory(User user, MultipartFile file, String caption) {
        try {
            String imageUrl = cloudinaryService.uploadImage(file);

            Story story = new Story();
            story.setUser(user);
            story.setImageUrl(imageUrl);
            story.setCaption(caption);
            story.setCreatedAt(LocalDateTime.now());
            story.setExpiresAt(LocalDateTime.now().plus(24, ChronoUnit.HOURS));

            return storyRepository.save(story);
        } catch (IOException e) {
            throw new RuntimeException("Upload to Cloudinary failed", e);

        }
    }



    public void deleteExpiredStories() {
        List<Story> expired = storyRepository.findByExpiresAtBefore(LocalDateTime.now());
        storyRepository.deleteAll(expired);
    }
}

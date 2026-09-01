package org.example.apimywebsite.service;


import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.StoryRepository;
import org.example.apimywebsite.util.TransactionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
        String imageUrl;
        try {
            imageUrl = cloudinaryService.uploadImage(file);
        } catch (IOException e) {
            // H8b: external-service (Cloudinary) failure -> 500 with a fixed safe message.
            // The message text is unchanged from before; only the exception type/status
            // changed. The original IOException is preserved as the cause for server-side
            // diagnostics only - GlobalExceptionHandler.handleResponseStatusException never
            // reads the cause, only ex.getReason(), so it never reaches the client.
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Upload to Cloudinary failed", e);
        }

        Story story = new Story();
        story.setUser(user);
        story.setImageUrl(imageUrl);
        story.setCaption(caption);
        story.setCreatedAt(LocalDateTime.now());
        story.setExpiresAt(LocalDateTime.now().plus(24, ChronoUnit.HOURS));

        // COR-010 fix: the image is already uploaded by this point - if persisting the Story
        // row fails for any reason, that upload becomes a permanent orphan (no row will ever
        // reference it). Compensate by deleting the just-uploaded asset before propagating the
        // failure.
        try {
            return storyRepository.save(story);
        } catch (RuntimeException e) {
            cloudinaryService.deleteImage(imageUrl);
            throw e;
        }
    }



    // Was previously dead code (zero call sites) - reads were already safe (getVisibleStories
    // filters expiresAt > now), but expired rows and their Cloudinary assets accumulated forever.
    // Hourly is frequent enough given the 24h story lifetime (same reasoning as
    // ActiveChatTracker's hourly sweep for its 12h TTL). Spring's default single-threaded
    // TaskScheduler (no custom TaskScheduler/@EnableAsync configured anywhere in this app) never
    // overlaps invocations of the same @Scheduled method, so a run that takes longer than an hour
    // (e.g. many slow Cloudinary deletes) simply delays the next tick rather than running
    // concurrently with it - no explicit locking needed for this single-instance deployment.
    @Scheduled(fixedRate = 60 * 60 * 1000L)
    public void deleteExpiredStories() {
        List<Story> expired = storyRepository.findByExpiresAtBefore(LocalDateTime.now());
        if (expired.isEmpty()) {
            return;
        }
        storyRepository.deleteAll(expired);
        // COR-001 fix: deletion previously ran here, inside this still-open transaction (the
        // class-level @Transactional means the commit doesn't happen until this method returns).
        // Deferred to afterCommit so an irreversible Cloudinary delete can never run ahead of a
        // DB delete that might yet roll back. deleteImage remains defensive/idempotent (failures
        // are caught and logged inside CloudinaryService itself; Spring also logs rather than
        // propagates afterCommit exceptions), so a Cloudinary-side failure still can never affect
        // this scheduled job's own success - the orphaned asset just persists a little longer.
        List<String> imageUrls = expired.stream().map(Story::getImageUrl).toList();
        TransactionUtils.runAfterCommit(() -> imageUrls.forEach(cloudinaryService::deleteImage));
    }
}

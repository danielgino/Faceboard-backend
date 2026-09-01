package org.example.apimywebsite.service;

import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.repository.StoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * H8b: StoryService.uploadStory now throws a typed ResponseStatusException(500) with a
 * fixed, safe message when the Cloudinary upload fails, instead of a plain
 * RuntimeException("Upload to Cloudinary failed", e). The message text is unchanged (it was
 * already safe); what changed is the status (400 -> 500, matching "external-service
 * failure") and confirming the underlying IOException's own message never reaches the client.
 */
@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private FriendshipService friendshipService;
    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private StoryService storyService;

    @Test
    void uploadStory_cloudinaryFailure_throwsSafeInternalServerError_andNeverSaves() throws IOException {
        User user = User.builder().id(1).build();
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        String sensitiveProviderDetail = "Cloudinary API error: invalid api_key ck_live_9f2a... for cloud 'faceboard-prod'";
        when(cloudinaryService.uploadImage(file)).thenThrow(new IOException(sensitiveProviderDetail));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> storyService.uploadStory(user, file, "caption"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertEquals("Upload to Cloudinary failed", ex.getReason());
        assertFalse(ex.getReason().contains(sensitiveProviderDetail), "must not leak the provider's raw error detail");
        assertFalse(ex.getReason().toLowerCase().contains("api_key"), "must not leak credential-shaped text");
        verify(storyRepository, never()).save(any());
    }

    @Test
    void uploadStory_success_savesStory() throws IOException {
        User user = User.builder().id(1).build();
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(file)).thenReturn("https://cdn.example.com/story.png");
        when(storyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        storyService.uploadStory(user, file, "caption");

        verify(storyRepository).save(any());
    }

    // COR-010 fix: if persisting the Story row fails after the image has already been uploaded,
    // that upload becomes a permanent orphan (no row will ever reference it) unless compensated.
    @Test
    void uploadStory_whenSaveFails_deletesTheAlreadyUploadedImage() throws IOException {
        User user = User.builder().id(1).build();
        MultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});
        when(cloudinaryService.uploadImage(file)).thenReturn("https://cdn.example.com/story.png");
        when(storyRepository.save(any())).thenThrow(new RuntimeException("db connection reset"));

        assertThrows(RuntimeException.class, () -> storyService.uploadStory(user, file, "caption"));

        verify(cloudinaryService).deleteImage("https://cdn.example.com/story.png");
    }

    /**
     * Story expiration cleanup (previously dead code - deleteExpiredStories had zero call
     * sites): now wired to run on a schedule, deletes expired DB rows, and best-effort deletes
     * their Cloudinary assets after the DB delete has already succeeded.
     */
    @Test
    void deleteExpiredStories_deletesExpiredRows_andTheirCloudinaryImages_afterTheDbDelete() {
        Story expired1 = new Story();
        expired1.setImageUrl("https://res.cloudinary.com/demo/image/upload/v123/story1.png");
        Story expired2 = new Story();
        expired2.setImageUrl("https://res.cloudinary.com/demo/image/upload/v123/story2.png");
        when(storyRepository.findByExpiresAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(expired1, expired2));

        storyService.deleteExpiredStories();

        InOrder inOrder = inOrder(storyRepository, cloudinaryService);
        inOrder.verify(storyRepository).deleteAll(List.of(expired1, expired2));
        inOrder.verify(cloudinaryService).deleteImage(expired1.getImageUrl());
        inOrder.verify(cloudinaryService).deleteImage(expired2.getImageUrl());
    }

    @Test
    void deleteExpiredStories_noExpiredStories_doesNothing() {
        when(storyRepository.findByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(List.of());

        storyService.deleteExpiredStories();

        verify(storyRepository, never()).deleteAll(any());
        verifyNoInteractions(cloudinaryService);
    }

    // COR-001 fix: same invariant as PostServiceTest's equivalent test - under a real
    // transaction, the Cloudinary delete must be deferred until after commit, not run eagerly
    // inside the still-open transaction. The test above runs with no real transaction manager
    // active (isSynchronizationActive() is false), exercising the immediate-fallback branch;
    // this test activates synchronization to prove the deferred branch itself.
    @Test
    void deleteExpiredStories_underActiveTransaction_defersCloudinaryDeleteUntilAfterCommit() {
        Story expired = new Story();
        expired.setImageUrl("https://res.cloudinary.com/demo/image/upload/v123/story1.png");
        when(storyRepository.findByExpiresAtBefore(any(LocalDateTime.class))).thenReturn(List.of(expired));

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            storyService.deleteExpiredStories();

            verify(storyRepository).deleteAll(List.of(expired));
            verify(cloudinaryService, never()).deleteImage(any());

            List<org.springframework.transaction.support.TransactionSynchronization> syncs =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, syncs.size());

            syncs.get(0).afterCommit();

            verify(cloudinaryService).deleteImage(expired.getImageUrl());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

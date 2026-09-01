package org.example.apimywebsite.api.controller;

import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.StoryDTO;
import org.example.apimywebsite.service.StoryService;
import org.example.apimywebsite.util.AuthHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-OOP1: StoryController previously took an injected Authentication parameter and manually
 * turned it into a User via userService.findByUserName - functionally identical to (and now
 * replaced by) AuthHelper.getCurrentUser(), matching the rest of the codebase.
 */
@ExtendWith(MockitoExtension.class)
class StoryControllerTest {

    @Mock
    private StoryService storyService;
    @Mock
    private AuthHelper authHelper;

    @Test
    void getVisibleStories_usesAuthHelperCurrentUser() {
        StoryController controller = new StoryController(storyService, authHelper);
        User currentUser = User.builder().id(1).userName("alice").build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        when(storyService.getVisibleStories(currentUser)).thenReturn(List.of());

        List<StoryDTO> result = controller.getVisibleStories();

        assertEquals(List.of(), result);
        verify(storyService).getVisibleStories(currentUser);
    }

    @Test
    void uploadStory_usesAuthHelperCurrentUser_asStoryOwner() {
        StoryController controller = new StoryController(storyService, authHelper);
        User currentUser = User.builder().id(1).userName("alice").build();
        when(authHelper.getCurrentUser()).thenReturn(currentUser);
        MultipartFile file = new MockMultipartFile("file", "story.png", "image/png", new byte[]{1, 2, 3});
        Story savedStory = new Story();
        savedStory.setUser(currentUser);
        when(storyService.uploadStory(currentUser, file, "caption")).thenReturn(savedStory);

        StoryDTO result = controller.uploadStory(file, "caption");

        assertEquals(currentUser.getId(), result.getUserId());
        verify(storyService).uploadStory(currentUser, file, "caption");
    }
}

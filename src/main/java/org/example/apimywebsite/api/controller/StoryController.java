package org.example.apimywebsite.api.controller;


import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.StoryDTO;
import org.example.apimywebsite.service.StoryService;
import org.example.apimywebsite.util.AuthHelper;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService storyService;
    private final AuthHelper authHelper;

    public StoryController(StoryService storyService, AuthHelper authHelper) {
        this.storyService = storyService;
        this.authHelper = authHelper;
    }

    // M-OOP1: additional duplicate found beyond the audit's original list - both methods here
    // manually turned an injected Authentication into a User via findByUserName, functionally
    // identical to (and now replaced by) AuthHelper.getCurrentUser().
    @GetMapping("/friends")
    public List<StoryDTO> getVisibleStories() {
        User user = authHelper.getCurrentUser();
        List<Story> stories = storyService.getVisibleStories(user);
        return stories.stream()
                .map(StoryDTO::new)
                .collect(Collectors.toList());
    }

    @PostMapping("/upload")
    public StoryDTO uploadStory(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption
    ) {
        User user = authHelper.getCurrentUser();
        Story story = storyService.uploadStory(user, file, caption);
        return new StoryDTO(story);
    }

}

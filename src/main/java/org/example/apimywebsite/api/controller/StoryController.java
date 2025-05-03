package org.example.apimywebsite.api.controller;


import org.example.apimywebsite.api.model.Story;
import org.example.apimywebsite.api.model.User;
import org.example.apimywebsite.dto.StoryDTO;
import org.example.apimywebsite.service.StoryService;
import org.example.apimywebsite.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService storyService;
    private final UserService userService;

    public StoryController(StoryService storyService, UserService userService) {
        this.storyService = storyService;
        this.userService = userService;
    }


    @GetMapping("/friends")
    public List<StoryDTO> getVisibleStories(Authentication authentication) {
        User user = userService.findByUserName(authentication.getName());
        List<Story> stories = storyService.getVisibleStories(user);
        return stories.stream()
                .map(StoryDTO::new)
                .collect(Collectors.toList());
    }

    @PostMapping("/upload")
    public StoryDTO uploadStory(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            Authentication authentication
    ) {
        User user = userService.findByUserName(authentication.getName());
        Story story = storyService.uploadStory(user, file, caption);
        return new StoryDTO(story);
    }

}

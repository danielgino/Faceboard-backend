package org.example.apimywebsite.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.apimywebsite.api.model.Story;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoryDTO {

    private Long id;
    private int userId;
    private String fullName;
    private String profilePictureUrl;
    private String imageUrl;
    private String caption;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;


    public StoryDTO(Story story) {
        this.id = story.getId();
        this.userId = story.getUser().getId();
        this.fullName = story.getUser().getFullName();
        this.profilePictureUrl = story.getUser().getProfilePictureUrl();
        this.imageUrl = story.getImageUrl();
        this.caption = story.getCaption();
        this.createdAt = story.getCreatedAt();
        this.expiresAt = story.getExpiresAt();
    }
}

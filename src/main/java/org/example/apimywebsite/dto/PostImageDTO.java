package org.example.apimywebsite.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PostImageDTO {
    private Long id;
    private Long postId;
    private String imageUrl;

    public PostImageDTO() {}

    public PostImageDTO(Long id, Long postId, String imageUrl) {
        this.id = id;
        this.postId = postId;
        this.imageUrl = imageUrl;
    }

}

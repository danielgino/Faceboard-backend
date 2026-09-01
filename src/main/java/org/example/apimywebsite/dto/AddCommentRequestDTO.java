package org.example.apimywebsite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Comment-hijacking finding: an explicit allowlisted request model for POST /comments/add-comment.
// Deliberately contains only postId and text - no commentId, no user/author, no createdAt, no
// Post/Comment entity or persistence relationship - so none of those can ever be bound from the
// client, regardless of what a request body contains.
@Getter
@Setter
public class AddCommentRequestDTO {

    @NotNull(message = "postId is required")
    private Long postId;

    @NotBlank(message = "Comment text is required")
    private String text;
}

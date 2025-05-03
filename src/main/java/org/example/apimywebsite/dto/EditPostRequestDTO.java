package org.example.apimywebsite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditPostRequestDTO {

    @NotBlank(message = "Content must not be blank")
    @Size(max = 5000, message = "Content must be up to 5000 characters")
    private String content;
}
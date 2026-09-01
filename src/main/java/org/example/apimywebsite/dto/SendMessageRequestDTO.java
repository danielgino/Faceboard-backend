package org.example.apimywebsite.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// SEC-001 fix: request body for POST /messages/send. Deliberately carries only receiverId and
// message - no senderId - since the sender must always be derived from the authenticated
// principal, never taken from client input.
@Getter
@Setter
public class SendMessageRequestDTO {

    @NotNull(message = "receiverId is required")
    private Integer receiverId;

    @NotBlank(message = "Message content is required")
    private String message;
}

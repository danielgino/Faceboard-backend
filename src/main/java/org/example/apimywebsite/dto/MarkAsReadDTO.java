package org.example.apimywebsite.dto;

import lombok.Data;
@Data
public class MarkAsReadDTO {
    private int senderId;
    private int receiverId;
}

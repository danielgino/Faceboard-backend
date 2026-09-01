package org.example.apimywebsite.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;


// API-001 fix: this DTO doubles as the STOMP /app/sendMessage request payload (see
// WebSocketController.handleMessage) as well as the outbound REST/STOMP response shape - a
// null/blank message previously reached MessageService.sendMessage unvalidated over STOMP
// (REST's equivalent, SendMessageRequestDTO, already rejects this), failing late as a raw DB
// constraint violation with no clean STOMP error instead of being rejected up front like REST.
// Validation only ever fires where a caller explicitly triggers it (@Valid on the STOMP payload
// parameter); constructing/returning an instance elsewhere, e.g. as a response DTO, is unaffected.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    @JsonProperty("id")
    private int id;

    @JsonProperty("message")
    @NotBlank(message = "Message content is required")
    private String message;

    @JsonProperty("sentTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime sentTime;

    @JsonProperty("senderId")
    private int senderId;

    @JsonProperty("receiverId")
    @Positive(message = "receiverId must be a positive user id")
    private int receiverId;

    @JsonProperty("isRead")
    private boolean isRead = false;
}



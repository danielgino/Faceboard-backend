package org.example.apimywebsite.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    @JsonProperty("id")
    private int id;

    @JsonProperty("message")
    private String message;

    @JsonProperty("sentTime")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private OffsetDateTime sentTime;

    @JsonProperty("senderId")
    private int senderId;

    @JsonProperty("receiverId")
    private int receiverId;

    @JsonProperty("isRead")
    private boolean isRead = false;
}



package org.example.apimywebsite.exception;

public class MessageParticipantNotFoundException extends RuntimeException {
    public MessageParticipantNotFoundException(String message) {
        super(message);
    }
}

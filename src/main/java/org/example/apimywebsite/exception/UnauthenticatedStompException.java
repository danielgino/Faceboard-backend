package org.example.apimywebsite.exception;

public class UnauthenticatedStompException extends RuntimeException {
    public UnauthenticatedStompException(String message) {
        super(message);
    }
}

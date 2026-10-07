package dev.aziz.highloadasyncservice.exception;

public class InvalidTaskMessageException extends RuntimeException {
    public InvalidTaskMessageException(String message) {
        super(message);
    }
}
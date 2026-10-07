package dev.aziz.highloadasyncservice.exception;

public class TaskPublishingException extends RuntimeException {
    public TaskPublishingException(String message, Throwable cause) {
        super(message, cause);
    }
}
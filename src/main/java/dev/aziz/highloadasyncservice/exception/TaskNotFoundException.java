package dev.aziz.highloadasyncservice.exception;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(String message) {
        super(message);
    }

    public TaskNotFoundException(Long id) {
        super("Task with id " + id + " not found");
    }
}

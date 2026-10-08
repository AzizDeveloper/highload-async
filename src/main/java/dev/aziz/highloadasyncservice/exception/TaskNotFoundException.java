package dev.aziz.highloadasyncservice.exception;

import java.util.UUID;

public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(Long id) {
        super("Задача с ID " + id + " не найдена");
    }

    public TaskNotFoundException(UUID eventId) {
        super("Задача с EventId " + eventId + " не найдена");
    }
}
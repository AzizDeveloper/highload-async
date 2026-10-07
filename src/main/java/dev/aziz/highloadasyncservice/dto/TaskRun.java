package dev.aziz.highloadasyncservice.dto;

import java.util.UUID;

public record TaskRun(
        Long id,
        UUID executionId,
        long duration
) {
}


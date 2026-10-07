package dev.aziz.highloadasyncservice.dto;

import dev.aziz.highloadasyncservice.model.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record TaskResponseDTO(
        Long id,
        UUID eventId,
        String name,
        @Schema(description = "Длительность в миллисекундах") long duration,
        TaskStatus status,
        int progress,
        String result,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}

package dev.aziz.highloadasyncservice.dto;

import java.util.UUID;

public record ProcessTaskCommand(
        String name,
        Long duration,
        UUID idempotencyKey
) {}
package dev.aziz.highloadasyncservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class TaskRequestDTO {

    @NotNull(message = "Идентификатор события обязателен")
    private UUID eventId;

    @NotBlank(message = "Название задачи обязательно")
    @Size(max = 255, message = "Название не должно превышать 255 символов")
    private String name;

    @NotNull(message = "Длительность обязательна")
    @Positive(message = "Длительность должна быть положительной")
    private Long duration;

}

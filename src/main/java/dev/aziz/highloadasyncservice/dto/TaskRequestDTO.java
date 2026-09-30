package dev.aziz.highloadasyncservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class TaskRequestDTO {
    @NotBlank(message = "Название задачи обязательно")
    private String name;

    @Positive(message = "Длительность должна быть положительной")
    private Long duration;
}

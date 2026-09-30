package dev.aziz.highloadasyncservice.dto;

import dev.aziz.highloadasyncservice.model.TaskStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponseDTO {

    private Long id;
    private String name;
    private Long duration;
    private TaskStatus status;
    private String result;

}

package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskQueryService {
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public TaskResponseDTO findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        return new TaskResponseDTO(
                task.getId(),
                task.getEventId(),
                task.getName(),
                task.getDuration(),
                task.getStatus(),
                task.getProgress(),
                task.getResult(),
                task.getCreatedAt(),
                task.getStartedAt(),
                task.getFinishedAt()
        );
    }
}

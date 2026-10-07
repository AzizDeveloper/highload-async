package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskUpdateService {

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateProgress(Long id, UUID executionId, int progress) {
        if (progress < 0 || progress > 99) {
            throw new IllegalArgumentException("Промежуточный прогресс должен быть от 0 до 99");
        }
        return taskRepository.updateProgress(
                id, executionId, TaskStatus.IN_PROGRESS, progress
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateTaskStatus(
            Long id,
            UUID executionId,
            TaskStatus newStatus,
            int progress,
            String result
    ) {
        if (newStatus != TaskStatus.COMPLETED && newStatus != TaskStatus.FAILED) {
            throw new IllegalArgumentException("Ожидался финальный статус задачи");
        }
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("Прогресс должен быть от 0 до 100");
        }

        int finalProgress = newStatus == TaskStatus.COMPLETED ? 100 : Math.min(progress, 99);
        return taskRepository.finishExecution(
                id, executionId, TaskStatus.IN_PROGRESS, newStatus,
                finalProgress, result, Instant.now()
        ) == 1;
    }

}

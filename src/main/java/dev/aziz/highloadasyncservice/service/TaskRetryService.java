package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskRetryService {

    private final TaskRepository taskRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retryFailedTask(Long id) {
        Task task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        if (task.getStatus() != TaskStatus.FAILED) {
            return false;
        }

        task.setStatus(TaskStatus.NEW);
        task.setExecutionId(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setProgress(0);
        task.setResult(null);
        return true;
    }

}
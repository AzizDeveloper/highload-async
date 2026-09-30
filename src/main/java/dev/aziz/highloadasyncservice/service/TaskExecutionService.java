package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutorService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionService {
    private final TaskRepository taskRepository;
    private final ExecutorService executorService;
    private final TaskStatusService taskStatusService;

    // Обрати внимание - нет @Retryable!@Transactional
    public void processNewTaskWithLock() {
        // SKIP LOCKED делает всю магию
        taskRepository.findAndLockFirstNewTask()
                .ifPresent(task -> {
                    log.info("Worker picked task {} with SKIP LOCKED", task.getId());

                    // Обновляем статус на IN_PROGRESS
                    task.setStatus(TaskStatus.IN_PROGRESS);
                    Task savedTask = taskRepository.save(task);

                    // Запускаем асинхронное выполнение
                    executorService.submit(() ->
                            taskStatusService.executeTask(savedTask));
                });
        // Когда транзакция закрывается, блокировка автоматически снимается// Другие воркеры теперь видят эту задачу как IN_PROGRESS и не будут её брать
    }
}

package dev.aziz.highloadasyncservice.service;

import aj.org.objectweb.asm.commons.Remapper;
import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final TaskExecutionService taskExecutionService;
    private final TaskRepository taskRepository;

    // NEW задачи обрабатываем часто - каждую секунду
    @Scheduled(fixedDelay = 1000)
    public void processNewTasks() {
        taskExecutionService.processNewTaskWithLock();
    }

    // FAILED задачи обрабатываем реже - например, раз в минуту
    // Или вообще можно отключить, если FAILED - это невалидные данные
    @Scheduled(fixedDelay = 60000)
    public void processFailedTasks() {
        taskRepository.findFirstByStatus(TaskStatus.FAILED)
                .ifPresent(task -> {
                    // Здесь можно добавить дополнительную логику:
                    // - проверку, сколько раз задача уже падала
                    // - отправку в Dead Letter Queue после N неудач
                    // - уведомление админа
                    log.info("Retrying failed task {}", task.getId());
                    taskExecutionService.processNewTaskWithLock();
                });
    }

    public TaskResponseDTO findById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
        return toDTO(task);
    }

    private TaskResponseDTO toDTO(Task task) {
        return TaskResponseDTO.builder()
                .id(task.getId())
                .name(task.getName())
                .duration(task.getDuration())
                .status(task.getStatus())
                .result(task.getResult())
                .build();
    }
}
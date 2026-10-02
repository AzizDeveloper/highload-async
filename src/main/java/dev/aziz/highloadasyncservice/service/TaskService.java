package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.ProcessTaskCommand;
import dev.aziz.highloadasyncservice.dto.TaskRequestDTO;
import dev.aziz.highloadasyncservice.dto.TaskResponseDTO;
import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final TaskExecutionService taskExecutionService;
    private final TaskRepository taskRepository;
    private final KafkaTemplate<String, ProcessTaskCommand> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    public void processTask() {
        log.info("Starting task processing...");
        taskRepository.findFirstByStatus(TaskStatus.NEW)
                .ifPresent(taskExecutionService::processTaskWithRetry);
    }

    @Scheduled(fixedDelay = 60000)
    public void processFailedTasks() {
        log.info("Starting failed task processing...");
        taskRepository.findFirstByStatus(TaskStatus.FAILED)
                .ifPresent(task -> {
                    if (task.getRetryCount() < 3) {
                        try {
                            taskExecutionService.resetTask(task.getId());
                        } catch (Exception e) {
                            log.error("Failed to process the failed task {}", task.getId(), e);
                        }
                    } else {
                        log.info("Task {} has been sent to Dead Letter Queue", task.getId());
                        log.info("Task {} has been sent to admin.", task.getId());
                        log.info("Retrying failed task {}", task.getId());
                    }
                });
    }

    @Scheduled(fixedDelay = 60000)
    public void processStuckTasks() {
        log.info("Starting stuck tasks processing...");
        Instant threshold = Instant.now().minus(15, ChronoUnit.MINUTES);

        List<Task> stuckTasks = taskRepository.findTop20ByStatusAndStartedAtBeforeOrderByStartedAtAsc(
                TaskStatus.IN_PROGRESS, threshold
        );

        if (stuckTasks.isEmpty()) {
            return;
        }

        log.info("Found {} stuck tasks to evaluate.", stuckTasks.size());

        for (Task task : stuckTasks) {
            try {
                taskExecutionService.resetTask(task.getId());
            } catch (Exception e) {
                log.error("Failed to process stuck task {}", task.getId(), e);
            }
        }
    }

    public void sendTask(TaskRequestDTO taskRequestDTO) {
        ProcessTaskCommand processTaskCommand = new ProcessTaskCommand(
                taskRequestDTO.getName(),
                taskRequestDTO.getDuration(),
                UUID.randomUUID()
        );
        kafkaTemplate.send("task-topic", processTaskCommand);
    }

    @KafkaListener(
            topics = "task-topic",
            groupId = "task-group"
    )
    public void consume(ProcessTaskCommand processTaskCommand) {
        Task task = new Task();
        task.setStatus(TaskStatus.NEW);
        task.setDuration(processTaskCommand.duration());
        task.setName(processTaskCommand.name());
        task.setIdempotencyKey(processTaskCommand.idempotencyKey());
        taskRepository.save(task);
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
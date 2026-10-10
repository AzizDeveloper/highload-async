package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckTaskRetryScheduler {

    private final TaskRepository taskRepository;

    @Scheduled(fixedDelayString = "${task.retry.check-delay-ms:60000}")
    @Transactional
    public void retryStuckTasks() {
        // TODO: stuck task processing logic.  All magic SQLs and etc.
        try {
            int recoveredCount = taskRepository.recoverStuckTasks();
            if (recoveredCount > 0) {
                log.info("Recovered {} expired IN_PROGRESS tasks", recoveredCount);
            }
        } catch (Exception e) {
            log.error("Failed to recover expired tasks", e);
        }
    }

}

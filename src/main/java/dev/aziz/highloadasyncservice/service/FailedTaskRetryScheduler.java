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
public class FailedTaskRetryScheduler {

    private final TaskRetryService taskRetryService;
    private final TaskRepository taskRepository;

    @Scheduled(fixedDelayString = "${task.retry.check-delay-ms:60000}")
    @Transactional
    public void retryEligibleFailedTasks() {
        try {
            int retriedCount = taskRepository.retryEligibleFailedTasks();
            if (retriedCount > 0) {
                log.info("Reset {} eligible FAILED tasks back to NEW", retriedCount);
            }
        } catch (Exception e) {
            log.error("Failed to retry eligible tasks", e);
        }
    }

}

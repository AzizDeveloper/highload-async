package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskClaimService taskClaimService;
    private final TaskMetrics taskMetrics;

    @Retryable(
            retryFor = {
                    OptimisticLockingFailureException.class,
                    OptimisticLockException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
    )
    public Optional<TaskRun> claimNextTask() {
        try {
            return taskClaimService.claimNextTask();
        } catch (OptimisticLockingFailureException | OptimisticLockException e) {
            taskMetrics.recordConflict();
            throw e;
        }
    }

    @Recover
    public Optional<TaskRun> recover(OptimisticLockingFailureException e) {
        log.warn("Не удалось захватить задачу после конфликтов версий", e);
        return Optional.empty();
    }

    @Recover
    public Optional<TaskRun> recover(OptimisticLockException e) {
        log.warn("Не удалось захватить задачу после конфликтов версий", e);
        return Optional.empty();
    }

}

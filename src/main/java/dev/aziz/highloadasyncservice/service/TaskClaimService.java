package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskClaimService {

    @Value("${lease.timeout.seconds:30}")
    private long leaseTimeoutSeconds;

    private final TaskRepository taskRepository;
    private final TransactionTemplate claimTransactionTemplate;

    public Optional<TaskRun> claimNextTask() {
        return claimTransactionTemplate.execute(transactionStatus ->
                taskRepository.findAndLockFirstNewTask()
                        .map(task -> {
                            task.setStatus(TaskStatus.IN_PROGRESS);
                            task.setExecutionId(UUID.randomUUID());
                            task.setStartedAt(Instant.now());
                            task.setFinishedAt(null);
                            task.setAttempts(task.getAttempts() + 1);
                            task.setProgress(0);
                            task.setResult(null);
                            task.setLeaseUntil(Instant.now().plusSeconds(leaseTimeoutSeconds));
                            taskRepository.saveAndFlush(task);

                            return new TaskRun(
                                    task.getId(), task.getExecutionId(), task.getDuration()
                            );
                        })
        );
    }

}

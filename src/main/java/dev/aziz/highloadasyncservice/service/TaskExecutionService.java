package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskClaimService taskClaimService;
    private final TaskMetrics taskMetrics;

    public Optional<TaskRun> claimNextTask() {
        try {
            return taskClaimService.claimNextTask();
        } catch (Exception e) {
            taskMetrics.recordTaskClaimError();
            throw e;
        }
    }

}

package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {
    private final ThreadPoolExecutor taskExecutor;
    private final TaskExecutionService taskExecutionService;
    private final TaskStatusService taskStatusService;
    private volatile boolean stopping;

    @Scheduled(fixedDelayString = "${worker.poll-delay-ms:1000}")
    public void processTask() {
        if (stopping || taskExecutor.isShutdown()) {
            return;
        }

        for (int i = 0; i < taskExecutor.getMaximumPoolSize(); i++) {
            try {
                // Сначала получаем место в пуле, потом воркер идет в БД
                taskExecutor.execute(this::runWorker);
            } catch (RejectedExecutionException e) {
                // Свободных потоков нет или уже началось завершение
                break;
            }
        }
    }

    private void runWorker() {
        try {
            while (!stopping && !Thread.currentThread().isInterrupted()) {
                Optional<TaskRun> run = taskExecutionService.claimNextTask();
                if (run.isEmpty()) {
                    return;
                }
                taskStatusService.executeTask(run.get());
            }
        } catch (RuntimeException e) {
            log.error("Ошибка рабочего цикла", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        stopping = true;
        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                taskExecutor.shutdownNow();
                if (!taskExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Не все воркеры завершились после прерывания");
                }
            }
        } catch (InterruptedException e) {
            taskExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
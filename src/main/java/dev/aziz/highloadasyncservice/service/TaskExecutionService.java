package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.exception.TaskNotFoundException;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskExecutionService {
    // Репозиторий для работы с задачами в БД
    private final TaskRepository taskRepository;
    // Пул потоков для асинхронного выполнения задач
    private final ExecutorService executorService;
    // Менеджер транзакций, инжектируется Spring'ом
    private final PlatformTransactionManager transactionManager;
    // Сервис для обновления статусов задач
    private final TaskStatusService taskStatusService;
    private final TaskMetrics taskMetrics;

    @Retryable(
            value = ObjectOptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 500 // backoff = @Backoff(delay = 500)
    )
    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRES_NEW
    )
    public void processTaskWithRetry(Task task) {
        try {
            Task currentTask = taskRepository.findById(task.getId())
                    .orElseThrow(() -> new TaskNotFoundException(task.getId()));

            // Проверяем, не взята ли задача уже в работу
            if (currentTask.getStatus() != TaskStatus.NEW) {
                // Логируем предупреждение, если задача уже в обработке
                log.warn("Task {} already in processing", task.getId());
                return;
            }

            // Меняем статус задачи на IN_PROGRESS
            currentTask.setStatus(TaskStatus.IN_PROGRESS);
            currentTask.setStartedAt(Instant.now());
            currentTask.setRetryCount(currentTask.getRetryCount() + 1);
            // Сохраняем обновленное состояние задачи
            Task savedTask = taskRepository.save(currentTask);

            // Запускаем асинхронное выполнение задачи в отдельном потоке
            executorService.submit(() -> executeTaskWithNewConnection(savedTask));

        } catch (OptimisticLockException e) {
            // Логируем обнаружение конкурентной модификации
            log.warn("Concurrent modification detected for task {}", task.getId());
            // Пробрасываем исключение для повторной попытки
            throw e;
        }
    }

    // Метод для выполнения задачи в новой транзакции
    private void executeTaskWithNewConnection(Task task) {
        // Создаем шаблон транзакции
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        // Устанавливаем уровень изоляции
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // Устанавливаем режим распространения транзакции
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // Выполняем код в транзакции
        transactionTemplate.execute(status -> {
            try {
                // Имитируем выполнение задачи
                Thread.sleep(task.getDuration());
                // Обновляем статус задачи на COMPLETED при успешном выполнении
                taskStatusService.updateTaskStatus(
                        task.getId(),
                        TaskStatus.COMPLETED,
                        "Task completed successfully"
                );
            } catch (InterruptedException e) {
                // Восстанавливаем флаг прерывания
                Thread.currentThread().interrupt();
                // Обновляем статус на FAILED при прерывании
                taskStatusService.updateTaskStatus(
                        task.getId(),
                        TaskStatus.FAILED,
                        "Task interrupted: " + e.getMessage()
                );
            } catch (Exception e) {
                // Логируем ошибку выполнения
                log.error("Error executing task {}", task.getId(), e);
                // Обновляем статус на FAILED при любой другой ошибке
                taskStatusService.updateTaskStatus(
                        task.getId(),
                        TaskStatus.FAILED,
                        "Error: " + e.getMessage()
                );
            }
            return null;
        });
    }

    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRES_NEW
    )
    public void resetTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        if (task.getStatus() != TaskStatus.IN_PROGRESS &&
                task.getStatus() != TaskStatus.FAILED) {
            return;
        }
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            log.error("Thread.sleep failed. TaskExecutionService - {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
        if (task.getRetryCount() >= 3) {
            if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                task.setStatus(TaskStatus.FAILED);
                log.warn("Task {} reached max retries while stuck in progress. Marking as FAILED.", taskId);
            } else {
                return;
            }
        } else {
            task.setStatus(TaskStatus.NEW);
            task.setStartedAt(null);
            log.info("Task {} is stuck. Resetting to NEW. Retry count: {}", taskId, task.getRetryCount());
        }

        try {
            taskRepository.save(task);
        } catch (ObjectOptimisticLockingFailureException e) {
            taskMetrics.recordConflict();
            log.warn("Task {} was already updated by another thread/worker. Skipping.", taskId);
        }
    }

    @PreDestroy
    public void shutdown() {
        // Инициируем корректное завершение пула потоков
        executorService.shutdown();
        try {
            // Ждем завершения всех задач в течение 60 секунд
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                // Если задачи не завершились, принудительно останавливаем
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            // При прерывании принудительно останавливаем пул
            executorService.shutdownNow();
            // Восстанавливаем флаг прерывания
            Thread.currentThread().interrupt();
        }
    }

}

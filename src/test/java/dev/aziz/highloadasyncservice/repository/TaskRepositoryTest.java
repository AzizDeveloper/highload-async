package dev.aziz.highloadasyncservice.repository;

import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
class TaskRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(transactionManager);
        taskRepository.deleteAll();
    }

    private Task newTask(String name, long duration, TaskStatus status) {
        Task task = new Task();
        task.setEventId(UUID.randomUUID());
        task.setName(name);
        task.setDuration(duration);
        task.setStatus(status);
        task.setProgress(0);
        task.setAttempts(0);
        task.setMaxAttempts(3);

        if (status == TaskStatus.IN_PROGRESS) {
            task.setExecutionId(UUID.randomUUID());
            task.setStartedAt(Instant.now().minusSeconds(120));
            task.setLeaseUntil(Instant.now().plusSeconds(30));
        }

        if (status == TaskStatus.FAILED) {
            task.setStartedAt(Instant.now().minusSeconds(120));
            task.setFinishedAt(Instant.now().minusSeconds(60));
            task.setResult("Task failed");
        }

        if (status == TaskStatus.COMPLETED) {
            task.setProgress(100);
            task.setStartedAt(Instant.now().minusSeconds(120));
            task.setFinishedAt(Instant.now().minusSeconds(60));
            task.setResult("Task completed");
        }

        return task;
    }

    private Task save(Task task) {
        return taskRepository.saveAndFlush(task);
    }

    private Task createTask(String name, long duration, TaskStatus status) {
        Task task = new Task();
        task.setEventId(UUID.randomUUID());
        task.setName(name);
        task.setDuration(duration);
        task.setStatus(status);
        task.setAttempts(0);
        task.setMaxAttempts(3);
        task.setProgress(0);
        return task;
    }

    // ==========================================
    // Basic Idempotency & Conditional Updates
    // ==========================================

    @Test
    void insertIfAbsent_shouldInsertWhenEventIdDoesNotExist() {
        UUID eventId = UUID.randomUUID();

        int inserted = taskRepository.insertIfAbsent(eventId, "task", 100L);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void insertIfAbsent_shouldReturnZeroWhenEventIdAlreadyExists() {
        UUID eventId = UUID.randomUUID();

        int firstInsert = taskRepository.insertIfAbsent(eventId, "task", 100L);
        int secondInsert = taskRepository.insertIfAbsent(eventId, "task", 100L);

        assertThat(firstInsert).isEqualTo(1);
        assertThat(secondInsert).isZero();
    }

    @Test
    void updateProgress_shouldUpdateWhenExecutionIdAndStatusMatch() {
        Task task = save(newTask("progress task", 100L, TaskStatus.IN_PROGRESS));

        int updated = taskRepository.updateProgress(
                task.getId(),
                task.getExecutionId(),
                TaskStatus.IN_PROGRESS,
                35
        );

        assertThat(updated).isEqualTo(1);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getProgress()).isEqualTo(35);
    }

    @Test
    void updateProgress_shouldNotUpdateWhenExecutionIdDoesNotMatch() {
        Task task = save(newTask("progress task", 100L, TaskStatus.IN_PROGRESS));

        int updated = taskRepository.updateProgress(
                task.getId(),
                UUID.randomUUID(),
                TaskStatus.IN_PROGRESS,
                35
        );

        assertThat(updated).isZero();

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getProgress()).isZero();
    }

    @Test
    void finishExecution_shouldMarkTaskCompleted() {
        Task task = save(newTask("finish task", 100L, TaskStatus.IN_PROGRESS));

        int updated = taskRepository.finishExecution(
                task.getId(),
                task.getExecutionId(),
                TaskStatus.IN_PROGRESS,
                TaskStatus.COMPLETED,
                100,
                "done",
                Instant.now()
        );

        assertThat(updated).isEqualTo(1);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(reloaded.getProgress()).isEqualTo(100);
        assertThat(reloaded.getFinishedAt()).isNotNull();
    }

    @Test
    void recoverStuckTasks_shouldResetExpiredInProgressTask() {
        Task stuckTask = newTask("stuck task", 100L, TaskStatus.IN_PROGRESS);
        stuckTask.setAttempts(1);
        stuckTask.setLeaseUntil(Instant.now().minusSeconds(60));

        save(stuckTask);

        int recovered = taskRepository.recoverStuckTasks();

        assertThat(recovered).isEqualTo(1);

        Task reloaded = taskRepository.findById(stuckTask.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(reloaded.getExecutionId()).isNull();
        assertThat(reloaded.getLeaseUntil()).isNull();
        assertThat(reloaded.getStartedAt()).isNull();
        assertThat(reloaded.getFinishedAt()).isNull();
        assertThat(reloaded.getProgress()).isZero();
        assertThat(reloaded.getNextRetryAt()).isNotNull();
    }

    @Test
    void retryEligibleFailedTasks_shouldResetFailedTaskToNew() {
        Task failedTask = newTask("failed task", 100L, TaskStatus.FAILED);
        failedTask.setAttempts(1);
        failedTask.setNextRetryAt(Instant.now().minusSeconds(10));

        save(failedTask);

        int retried = taskRepository.retryEligibleFailedTasks();

        assertThat(retried).isEqualTo(1);

        Task reloaded = taskRepository.findById(failedTask.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.NEW);
        assertThat(reloaded.getNextRetryAt()).isNull();
        assertThat(reloaded.getStartedAt()).isNull();
        assertThat(reloaded.getFinishedAt()).isNull();
        assertThat(reloaded.getProgress()).isZero();
        assertThat(reloaded.getResult()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrency_findAndLockFirstNewTask_shouldSkipLockedRow() throws Exception {
        Task task1 = save(newTask("task one", 100L, TaskStatus.NEW));
        Task task2 = save(newTask("task two", 100L, TaskStatus.NEW));

        CountDownLatch lockAcquired = new CountDownLatch(1);

        CompletableFuture<Task> tx1 = CompletableFuture.supplyAsync(() ->
                txTemplate.execute(status -> {
                    Task lockedTask = taskRepository.findByIdForUpdate(task1.getId())
                            .orElseThrow();

                    lockAcquired.countDown();

                    try {
                        Thread.sleep(700);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    return lockedTask;
                })
        );

        assertThat(lockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Task> tx2 = CompletableFuture.supplyAsync(() ->
                txTemplate.execute(status ->
                        taskRepository.findAndLockFirstNewTask().orElse(null)
                )
        );

        Task tx1Result = tx1.get(5, TimeUnit.SECONDS);
        Task tx2Result = tx2.get(5, TimeUnit.SECONDS);
        System.out.println("tx1Result.getName() = " + tx1Result.getName());
        System.out.println("tx2Result.getName() = " + tx2Result.getName());
        assertThat(tx1Result).isNotNull();
        assertThat(tx1Result.getId()).isEqualTo(task1.getId());

        assertThat(tx2Result).isNotNull();
        assertThat(tx2Result.getId()).isEqualTo(task2.getId());
    }
}

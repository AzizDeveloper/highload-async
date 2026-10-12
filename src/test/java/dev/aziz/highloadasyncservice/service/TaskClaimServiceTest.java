package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.model.Task;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskClaimServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TransactionTemplate claimTransactionTemplate;

    @InjectMocks
    private TaskClaimService taskClaimService;

    private Task task;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskClaimService, "leaseTimeoutSeconds", 30L);

        task = new Task();
        task.setId(1L);
        task.setEventId(UUID.randomUUID());
        task.setName("task");
        task.setDuration(42L);
        task.setStatus(TaskStatus.NEW);
        task.setAttempts(0);
        task.setProgress(0);

        when(claimTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void shouldReturnEmptyWhenNoNewTaskAvailable() {
        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.empty());

        Optional<TaskRun> result = taskClaimService.claimNextTask();

        assertThat(result).isEmpty();

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldClaimTaskAndMarkItAsInProgress() {
        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.of(task));

        Instant before = Instant.now();

        Optional<TaskRun> result = taskClaimService.claimNextTask();

        Instant after = Instant.now();

        assertThat(result).isPresent();

        TaskRun taskRun = result.orElseThrow();

        assertThat(taskRun.id()).isEqualTo(task.getId());
        assertThat(taskRun.executionId()).isEqualTo(task.getExecutionId());
        assertThat(taskRun.duration()).isEqualTo(task.getDuration());

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getExecutionId()).isNotNull();
        assertThat(task.getStartedAt()).isNotNull();
        assertThat(task.getFinishedAt()).isNull();
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getResult()).isNull();
        assertThat(task.getLeaseUntil()).isNotNull();

        assertThat(task.getStartedAt())
                .isBetween(before, after);

        assertThat(task.getLeaseUntil())
                .isBetween(before.plusSeconds(30), after.plusSeconds(30));

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository).saveAndFlush(task);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldIncrementExistingAttemptsCounter() {
        task.setAttempts(2);

        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.of(task));

        taskClaimService.claimNextTask();

        assertThat(task.getAttempts()).isEqualTo(3);

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository).saveAndFlush(task);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldResetPreviousExecutionStateWhenClaimingTask() {
        UUID oldExecutionId = UUID.randomUUID();
        Instant oldFinishedAt = Instant.now().minusSeconds(60);

        task.setExecutionId(oldExecutionId);
        task.setProgress(55);
        task.setResult("old result");
        task.setFinishedAt(oldFinishedAt);
        task.setAttempts(1);

        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.of(task));

        taskClaimService.claimNextTask();

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getExecutionId()).isNotEqualTo(oldExecutionId);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getResult()).isNull();
        assertThat(task.getFinishedAt()).isNull();
        assertThat(task.getAttempts()).isEqualTo(2);

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository).saveAndFlush(task);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldUseConfiguredLeaseTimeout() {
        ReflectionTestUtils.setField(taskClaimService, "leaseTimeoutSeconds", 90L);

        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.of(task));

        Instant before = Instant.now();

        taskClaimService.claimNextTask();

        Instant after = Instant.now();

        assertThat(task.getLeaseUntil())
                .isBetween(before.plusSeconds(90), after.plusSeconds(90));

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository).saveAndFlush(task);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldPropagateExceptionWhenFindingTaskFails() {
        when(taskRepository.findAndLockFirstNewTask())
                .thenThrow(new IllegalStateException("database error"));

        assertThrows(
                IllegalStateException.class,
                () -> taskClaimService.claimNextTask()
        );

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository, never()).saveAndFlush(any(Task.class));
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void shouldPropagateExceptionWhenSavingTaskFails() {
        when(taskRepository.findAndLockFirstNewTask())
                .thenReturn(Optional.of(task));

        when(taskRepository.saveAndFlush(task))
                .thenThrow(new IllegalStateException("save error"));

        assertThrows(
                IllegalStateException.class,
                () -> taskClaimService.claimNextTask()
        );

        verify(claimTransactionTemplate).execute(any());
        verify(taskRepository).findAndLockFirstNewTask();
        verify(taskRepository).saveAndFlush(task);
        verifyNoMoreInteractions(taskRepository);
    }

}

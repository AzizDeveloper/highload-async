package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.model.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStatusServiceTest {

    @Mock
    private TaskUpdateService taskUpdateService;

    @InjectMocks
    private TaskStatusService taskStatusService;

    private TaskRun taskRun;

    @BeforeEach
    void setUp() {
        // Default to 0 duration for fast, loop-skipping tests
        taskRun = new TaskRun(1L, UUID.randomUUID(), 0L);
    }

    // ==========================================
    // Happy Path Tests
    // ==========================================

    @Test
    void executeTask_shouldCompleteSuccessfully_whenDurationIsZero() {
        when(taskUpdateService.updateTaskStatus(
                eq(1L), eq(taskRun.executionId()), eq(TaskStatus.COMPLETED), eq(100), eq("Задача выполнена успешно")
        )).thenReturn(true);

        taskStatusService.executeTask(taskRun);

        // Loop is skipped entirely because elapsed (0) < duration (0) is false
        verify(taskUpdateService, never()).updateProgress(anyLong(), any(UUID.class), anyInt());
        verify(taskUpdateService).updateTaskStatus(
                1L, taskRun.executionId(), TaskStatus.COMPLETED, 100, "Задача выполнена успешно"
        );
    }

    @Test
    void executeTask_shouldUpdateProgressAndComplete_whenDurationIsSmall() {
        // Duration 1500ms ensures at least one loop iteration where elapsed (1000) < duration (1500)
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 1500L);

        when(taskUpdateService.updateProgress(eq(1L), eq(run.executionId()), anyInt())).thenReturn(true);
        when(taskUpdateService.updateTaskStatus(
                eq(1L), eq(run.executionId()), eq(TaskStatus.COMPLETED), eq(100), eq("Задача выполнена успешно")
        )).thenReturn(true);

        taskStatusService.executeTask(run);

        verify(taskUpdateService, atLeastOnce()).updateProgress(eq(1L), eq(run.executionId()), anyInt());
        verify(taskUpdateService).updateTaskStatus(
                1L, run.executionId(), TaskStatus.COMPLETED, 100, "Задача выполнена успешно"
        );
    }

    // ==========================================
    // Early Exit & Error Handling Tests
    // ==========================================

    @Test
    void executeTask_shouldAbort_whenProgressUpdateReturnsFalse() {
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 1500L);

        // Math: 1000ms elapsed out of 1500ms = 66.66% -> cast to int = 66
        when(taskUpdateService.updateProgress(1L, run.executionId(), 66))
                .thenReturn(false);

        taskStatusService.executeTask(run);

        verify(taskUpdateService).updateProgress(1L, run.executionId(), 66);
        // Must NOT call saveFinal because the task was stolen/cancelled
        verify(taskUpdateService, never()).updateTaskStatus(
                anyLong(), any(UUID.class), any(TaskStatus.class), anyInt(), anyString()
        );
    }

    @Test
    void executeTask_shouldAbortWithoutFailing_whenProgressUpdateThrowsDataAccessException() {
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 1500L);
        DataAccessException dbException = mock(DataAccessException.class);

        when(taskUpdateService.updateProgress(anyLong(), any(UUID.class), anyInt()))
                .thenThrow(dbException);

        taskStatusService.executeTask(run);

        // Should abort silently and NOT call saveFinal
        verify(taskUpdateService, never()).updateTaskStatus(
                anyLong(), any(UUID.class), any(TaskStatus.class), anyInt(), anyString()
        );
    }

    @Test
    void executeTask_shouldAbortWithoutFailing_whenProgressUpdateThrowsTransactionException() {
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 1500L);
        TransactionException txException = mock(TransactionException.class);

        when(taskUpdateService.updateProgress(anyLong(), any(UUID.class), anyInt()))
                .thenThrow(txException);

        taskStatusService.executeTask(run);

        verify(taskUpdateService, never()).updateTaskStatus(
                anyLong(), any(UUID.class), any(TaskStatus.class), anyInt(), anyString()
        );
    }

    @Test
    void executeTask_shouldFailTask_whenProgressUpdateThrowsRuntimeException() {
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 1500L);

        when(taskUpdateService.updateProgress(anyLong(), any(UUID.class), anyInt()))
                .thenThrow(new RuntimeException("Unexpected business error"));

        taskStatusService.executeTask(run);

        // Should catch RuntimeException and mark as FAILED
        verify(taskUpdateService).updateTaskStatus(
                eq(1L), eq(run.executionId()), eq(TaskStatus.FAILED), anyInt(), eq("Ошибка выполнения задачи")
        );
    }

    @Test
    void executeTask_shouldFailTaskAndRestoreInterruptFlag_whenInterrupted() {
        TaskRun run = new TaskRun(1L, UUID.randomUUID(), 5000L);

        when(taskUpdateService.updateTaskStatus(
                eq(1L), eq(run.executionId()), eq(TaskStatus.FAILED), anyInt(), eq("Выполнение прервано")
        )).thenReturn(true);

        // Simulate thread interruption before execution starts
        Thread.currentThread().interrupt();

        taskStatusService.executeTask(run);

        verify(taskUpdateService).updateTaskStatus(
                eq(1L), eq(run.executionId()), eq(TaskStatus.FAILED), anyInt(), eq("Выполнение прервано")
        );

        // Verify the service correctly restored the interrupt flag
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // Clean up interrupt flag so it doesn't break the test runner
        Thread.interrupted();
    }

    // ==========================================
    // Final Save (saveFinal) Edge Cases
    // ==========================================

    @Test
    void executeTask_shouldLogWarning_whenFinalUpdateReturnsFalse() {
        when(taskUpdateService.updateTaskStatus(
                anyLong(), any(UUID.class), any(TaskStatus.class), anyInt(), anyString()
        )).thenReturn(false);

        taskStatusService.executeTask(taskRun);

        verify(taskUpdateService).updateTaskStatus(
                1L, taskRun.executionId(), TaskStatus.COMPLETED, 100, "Задача выполнена успешно"
        );
    }

    @Test
    void executeTask_shouldCatchException_whenFinalUpdateThrowsRuntimeException() {
        when(taskUpdateService.updateTaskStatus(
                anyLong(), any(UUID.class), any(TaskStatus.class), anyInt(), anyString()
        )).thenThrow(new RuntimeException("DB crashed during final save"));

        // Should not throw exception up the stack
        assertDoesNotThrow(() -> taskStatusService.executeTask(taskRun));

        verify(taskUpdateService).updateTaskStatus(
                1L, taskRun.executionId(), TaskStatus.COMPLETED, 100, "Задача выполнена успешно"
        );
    }

}

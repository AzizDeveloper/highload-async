package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.model.TaskStatus;
import dev.aziz.highloadasyncservice.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskUpdateServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskUpdateService taskUpdateService;

    private final Long taskId = 1L;
    private final UUID executionId = UUID.randomUUID();

    // ==========================================
    // Tests for updateProgress
    // ==========================================

    @Test
    void updateProgress_shouldReturnTrue_whenRepositoryUpdatesOneRow() {
        when(taskRepository.updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 50))
                .thenReturn(1);

        boolean result = taskUpdateService.updateProgress(taskId, executionId, 50);

        assertThat(result).isTrue();
        verify(taskRepository).updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 50);
        verifyNoMoreInteractions(taskRepository);
    }

    @Test
    void updateProgress_shouldReturnFalse_whenRepositoryUpdatesZeroRows() {
        when(taskRepository.updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 50))
                .thenReturn(0);

        boolean result = taskUpdateService.updateProgress(taskId, executionId, 50);

        assertThat(result).isFalse();
        verify(taskRepository).updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 50);
    }

    @Test
    void updateProgress_shouldAllowZeroProgress() {
        when(taskRepository.updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 0))
                .thenReturn(1);

        boolean result = taskUpdateService.updateProgress(taskId, executionId, 0);

        assertThat(result).isTrue();
    }

    @Test
    void updateProgress_shouldAllow99Progress() {
        when(taskRepository.updateProgress(taskId, executionId, TaskStatus.IN_PROGRESS, 99))
                .thenReturn(1);

        boolean result = taskUpdateService.updateProgress(taskId, executionId, 99);

        assertThat(result).isTrue();
    }

    @Test
    void updateProgress_shouldThrow_whenProgressIsNegative() {
        assertThatThrownBy(() -> taskUpdateService.updateProgress(taskId, executionId, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Промежуточный прогресс должен быть от 0 до 99");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void updateProgress_shouldThrow_whenProgressIs100() {
        assertThatThrownBy(() -> taskUpdateService.updateProgress(taskId, executionId, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Промежуточный прогресс должен быть от 0 до 99");

        verifyNoInteractions(taskRepository);
    }

    // ==========================================
    // Tests for updateTaskStatus
    // ==========================================

    @Test
    void updateTaskStatus_shouldForceProgressTo100_whenStatusIsCompleted() {
        when(taskRepository.finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.COMPLETED),
                eq(100), eq("success"), any(Instant.class)
        )).thenReturn(1);

        boolean result = taskUpdateService.updateTaskStatus(
                taskId, executionId, TaskStatus.COMPLETED, 85, "success");

        assertThat(result).isTrue();

        verify(taskRepository).finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.COMPLETED),
                eq(100), eq("success"), any(Instant.class)
        );
    }

    @Test
    void updateTaskStatus_shouldNotReport100PercentProgress_whenStatusIsFailed() {
        when(taskRepository.finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.FAILED),
                eq(99), eq("error"), any(Instant.class)
        )).thenReturn(1);

        boolean result = taskUpdateService.updateTaskStatus(
                taskId, executionId, TaskStatus.FAILED, 100, "error");

        assertThat(result).isTrue();

        verify(taskRepository).finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.FAILED),
                eq(99), eq("error"), any(Instant.class)
        );
    }

    @Test
    void updateTaskStatus_shouldKeepProgressAsIs_whenStatusIsFailedAndBelow99() {
        when(taskRepository.finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.FAILED),
                eq(45), eq("error"), any(Instant.class)
        )).thenReturn(1);

        boolean result = taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.FAILED, 45, "error");

        assertThat(result).isTrue();

        verify(taskRepository).finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.FAILED),
                eq(45), eq("error"), any(Instant.class)
        );
    }

    @Test
    void updateTaskStatus_shouldReturnFalse_whenRepositoryUpdatesZeroRows() {
        when(taskRepository.finishExecution(
                eq(taskId), eq(executionId), eq(TaskStatus.IN_PROGRESS), eq(TaskStatus.COMPLETED),
                eq(100), eq("success"), any(Instant.class)
        )).thenReturn(0);

        boolean result = taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.COMPLETED, 100, "success");

        assertThat(result).isFalse();
    }

    @Test
    void updateTaskStatus_shouldThrow_whenStatusIsNotFinal() {
        assertThatThrownBy(() -> taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.NEW, 50, "result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ожидался финальный статус задачи");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void updateTaskStatus_shouldThrow_whenStatusIsInProgress() {
        assertThatThrownBy(() -> taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.IN_PROGRESS, 50, "result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ожидался финальный статус задачи");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void updateTaskStatus_shouldThrow_whenProgressIsNegative() {
        assertThatThrownBy(() -> taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.COMPLETED, -1, "result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Прогресс должен быть от 0 до 100");

        verifyNoInteractions(taskRepository);
    }

    @Test
    void updateTaskStatus_shouldThrow_whenProgressIsGreaterThan100() {
        assertThatThrownBy(() -> taskUpdateService.updateTaskStatus(taskId, executionId, TaskStatus.COMPLETED, 101, "result"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Прогресс должен быть от 0 до 100");

        verifyNoInteractions(taskRepository);
    }

}

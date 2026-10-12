package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExecutionServiceTest {

    @Mock
    private TaskClaimService taskClaimService;

    @Mock
    private TaskMetrics taskMetrics;

    @InjectMocks
    private TaskExecutionService taskExecutionService;

    @Test
    void shouldReturnTaskWhenClaimSucceeds() {
        TaskRun taskRun = new TaskRun(1L, UUID.randomUUID(), 10L);
        when(taskClaimService.claimNextTask()).thenReturn(Optional.of(taskRun));

        Optional<TaskRun> result = taskExecutionService.claimNextTask();

        assertThat(result).isPresent().contains(taskRun);
        verify(taskClaimService).claimNextTask();
        verifyNoInteractions(taskMetrics);
    }

    @Test
    void shouldReturnEmptyWhenNoTaskAvailable() {
        when(taskClaimService.claimNextTask()).thenReturn(Optional.empty());

        Optional<TaskRun> result = taskExecutionService.claimNextTask();

        assertThat(result).isEmpty();
        verify(taskClaimService).claimNextTask();
        verifyNoInteractions(taskMetrics);
    }

    @Test
    void shouldRecordMetricAndRethrowWhenClaimFails() {
        RuntimeException exception = new RuntimeException("DB error");
        when(taskClaimService.claimNextTask()).thenThrow(exception);

        assertThatThrownBy(() -> taskExecutionService.claimNextTask())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(taskClaimService).claimNextTask();
        verify(taskMetrics).recordTaskClaimError();
    }
}
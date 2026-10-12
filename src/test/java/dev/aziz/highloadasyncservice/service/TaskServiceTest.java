package dev.aziz.highloadasyncservice.service;

import dev.aziz.highloadasyncservice.dto.TaskRun;
import dev.aziz.highloadasyncservice.observability.metrics.TaskMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private ThreadPoolExecutor taskExecutor;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private TaskStatusService taskStatusService;

    @InjectMocks
    private TaskService taskService;

    @Test
    void shouldNotSubmitTasksWhenStoppingIsTrue() {
        ReflectionTestUtils.setField(taskService, "stopping", true);

        taskService.processTask();

        verify(taskExecutor, never()).execute(any());
    }

    @Test
    void shouldNotSubmitTasksWhenExecutorIsShutdown() {
        when(taskExecutor.isShutdown()).thenReturn(true);

        taskService.processTask();

        verify(taskExecutor, never()).execute(any());
    }

    @Test
    void shouldSubmitTasksUpToMaximumPoolSize() {
        when(taskExecutor.isShutdown()).thenReturn(false);
        when(taskExecutor.getMaximumPoolSize()).thenReturn(4);

        taskService.processTask();

        verify(taskExecutor, times(4)).execute(any(Runnable.class));
    }

    @Test
    void shouldStopSubmittingOnRejectedExecutionException() {
        when(taskExecutor.getMaximumPoolSize()).thenReturn(4);
        when(taskExecutor.isShutdown()).thenReturn(false);

        doNothing().doNothing().doThrow(new RejectedExecutionException())
                .when(taskExecutor).execute(any(Runnable.class));

        taskService.processTask();

        verify(taskExecutor, times(3)).execute(any(Runnable.class));
    }

    @Test
    void workerShouldProcessTaskAndStopWhenQueueIsEmpty() {
        when(taskExecutor.getMaximumPoolSize()).thenReturn(1);
        when(taskExecutor.isShutdown()).thenReturn(false);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        taskService.processTask();
        verify(taskExecutor).execute(runnableCaptor.capture());

        TaskRun taskRun = new TaskRun(1L, UUID.randomUUID(), 10L);
        when(taskExecutionService.claimNextTask())
                .thenReturn(Optional.of(taskRun))
                .thenReturn(Optional.empty());

        runnableCaptor.getValue().run();

        verify(taskExecutionService, times(2)).claimNextTask();
        verify(taskStatusService).executeTask(taskRun);
    }

    @Test
    void workerShouldCatchExceptionThrownByExecuteTask() {
        when(taskExecutor.getMaximumPoolSize()).thenReturn(1);
        when(taskExecutor.isShutdown()).thenReturn(false);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        taskService.processTask();
        verify(taskExecutor).execute(runnableCaptor.capture());

        TaskRun taskRun = new TaskRun(1L, UUID.randomUUID(), 10L);
        when(taskExecutionService.claimNextTask()).thenReturn(Optional.of(taskRun));

        doThrow(new RuntimeException("Execution failed"))
                .when(taskStatusService).executeTask(taskRun);

        runnableCaptor.getValue().run();

        verify(taskStatusService).executeTask(taskRun);
        // Exception caught by runWorker's catch block, so it stops and doesn't loop again
        verify(taskExecutionService, times(1)).claimNextTask();
    }

    @Test
    void workerShouldStopAndLogOnClaimException() {
        when(taskExecutor.getMaximumPoolSize()).thenReturn(1);
        when(taskExecutor.isShutdown()).thenReturn(false);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        taskService.processTask();
        verify(taskExecutor).execute(runnableCaptor.capture());

        when(taskExecutionService.claimNextTask()).thenThrow(new RuntimeException("DB error"));

        runnableCaptor.getValue().run();

        verify(taskExecutionService).claimNextTask();
        verifyNoInteractions(taskStatusService);
    }

    @Test
    void shutdownShouldStopExecutorGracefully() throws InterruptedException {
        when(taskExecutor.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(true);

        taskService.shutdown();

        assertThat((Boolean) ReflectionTestUtils.getField(taskService, "stopping")).isTrue();
        verify(taskExecutor).shutdown();
        verify(taskExecutor).awaitTermination(60, TimeUnit.SECONDS);
        verify(taskExecutor, never()).shutdownNow();
    }

    @Test
    void shutdownShouldForceStopIfGracefulFails() throws InterruptedException {
        when(taskExecutor.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false);
        when(taskExecutor.awaitTermination(5, TimeUnit.SECONDS)).thenReturn(true);

        taskService.shutdown();

        verify(taskExecutor).shutdown();
        verify(taskExecutor).shutdownNow();
        verify(taskExecutor).awaitTermination(60, TimeUnit.SECONDS);
        verify(taskExecutor).awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void shutdownShouldHandleInterruptedException() throws InterruptedException {
        when(taskExecutor.awaitTermination(60, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        taskService.shutdown();

        verify(taskExecutor).shutdown();
        verify(taskExecutor).shutdownNow();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
    }
}
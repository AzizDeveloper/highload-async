package dev.aziz.highloadasyncservice.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TaskMetrics {
    private final Counter optimisticLockConflicts;
    private final Counter taskClaimErrorCounts;

    private final Timer executionTimer;
    private final Counter completedCounter;
    private final Counter failedCounter;

    public TaskMetrics(MeterRegistry registry) {
        this.optimisticLockConflicts = Counter.builder("task.conflicts")
                .description("Number of optimistic lock conflicts")
                .register(registry);
        this.taskClaimErrorCounts = Counter.builder("task.claim.errors.count")
                .description("Number of task claim errors")
                .register(registry);

        this.executionTimer = Timer.builder("tasks.execution")
                .description("Task execution duration")
                .publishPercentileHistogram()
                .register(registry);

        this.completedCounter = Counter.builder("tasks.completed")
                .description("Successfully completed task executions")
                .register(registry);

        this.failedCounter = Counter.builder("tasks.failed")
                .description("Failed task executions")
                .register(registry);
    }

    public void recordExecution(Runnable operation) {
        executionTimer.record(() -> {
            try {
                operation.run();
                completedCounter.increment();
            } catch (RuntimeException e) {
                failedCounter.increment();
                throw e;
            }
        });
    }

    public void recordConflict() {
        optimisticLockConflicts.increment();
    }

    public void recordTaskClaimError() {
        taskClaimErrorCounts.increment();
    }

    public void recordSuccess() { completedCounter.increment(); }
    public void recordFailure() { failedCounter.increment(); }
    public void recordDuration(long nanos) { executionTimer.record(nanos, TimeUnit.NANOSECONDS); }


}

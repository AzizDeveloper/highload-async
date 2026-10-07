package dev.aziz.highloadasyncservice.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TaskMetrics {
    private final Counter optimisticLockConflicts;

    public TaskMetrics(MeterRegistry registry) {
        this.optimisticLockConflicts = Counter.builder("task.conflicts")
                .description("Number of optimistic lock conflicts")
                .register(registry);
    }

    public void recordConflict() {
        optimisticLockConflicts.increment();
    }
}

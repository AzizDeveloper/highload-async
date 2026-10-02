package dev.aziz.highloadasyncservice.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskMetrics {

    private final MeterRegistry registry;

    private final Counter optimisticLockConflicts = Counter.builder("task.conflicts")
        .description("Number of optimistic lock conflicts")
        .register(registry);

    public void recordConflict() {
        optimisticLockConflicts.increment();
    }

}

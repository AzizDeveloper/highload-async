package dev.aziz.highloadasyncservice.config;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.HikariCPSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

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

/*    @Bean
    public MeterBinder hikariMetrics(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            return new HikariMetrics((HikariDataSource) dataSource);
        }
        return null;
    }*/

}

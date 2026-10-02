package dev.aziz.highloadasyncservice.observability.metrics;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConnectionPoolMonitor {
    private final HikariDataSource dataSource;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedRate = 60000) // каждую минуту
    public void monitorConnectionPool() {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

        log.info("Connection pool status: active={}, idle={}, total={}",
            poolMXBean.getActiveConnections(),
            poolMXBean.getIdleConnections(),
            poolMXBean.getTotalConnections()
        );

        // Регистрируем метрики
        meterRegistry.gauge("hikari.pool.active.connections",
            poolMXBean.getActiveConnections());
        meterRegistry.gauge("hikari.pool.idle.connections",
            poolMXBean.getIdleConnections());
    }
}

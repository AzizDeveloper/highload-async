package dev.aziz.highloadasyncservice.observability.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@RequiredArgsConstructor
@Component
public class DatabaseHealthCheck extends AbstractHealthIndicator {
    private final DataSource dataSource;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1000)) {
                builder.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "UP");
            } else {
                builder.down()
                    .withDetail("error", "Database connection is invalid");
            }
        } catch (SQLException e) {
            builder.down()
                .withDetail("error", e.getMessage());
        }
    }
}

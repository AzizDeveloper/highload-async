package dev.aziz.highloadasyncservice.IT;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EmptyDbMigrationIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void emptyDatabaseIsMigratedToLatestSuccessfulState() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        postgres.getUsername(),
                        postgres.getPassword()
                )
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().all()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getState())
                .isEqualTo(MigrationState.SUCCESS);
    }

}

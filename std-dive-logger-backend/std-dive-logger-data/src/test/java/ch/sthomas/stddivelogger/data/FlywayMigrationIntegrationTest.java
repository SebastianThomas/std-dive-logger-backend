package ch.sthomas.stddivelogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Confirms every migration under db/migration/postgresql applies cleanly, in order, to a fresh real
 * Postgres instance - the exact same thing a real deployment does on boot.
 *
 * <p>Deliberately uses Flyway's plain Java API directly against Testcontainers rather than a full
 * {@code @SpringBootTest}, so this is unaffected by unrelated application-context wiring
 * (JPA/Hibernate, security, etc.) - it only proves the migration set itself is valid.
 */
@Testcontainers
class FlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @Test
    void allMigrationsApplyCleanlyToAFreshDatabase() {
        final var flyway =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword())
                        .locations("classpath:db/migration/postgresql")
                        .load();

        final var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);
    }
}

package ch.sthomas.stddivelogger.ws.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.Objects;

@Testcontainers
class RelativeTimeMigrationTest {
    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"));

    @Test
    void migrationPreservesAbsoluteSamplesAndNullableWindowBounds() throws Exception {
        try (final var connection =
                        DriverManager.getConnection(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword());
                final var statement = connection.createStatement()) {
            statement.execute(
                    """
                CREATE TABLE t_dive_profiles(pk_dive_profile_id bigint, dive_profile_start timestamptz);
                CREATE TABLE t_dive_measurements(fk_dive_profile_id bigint, time timestamptz);
                CREATE TABLE t_dive_summary(fk_dive_id bigint, dive_start timestamptz);
                CREATE TABLE t_dive_configuration_cylinder(pk_configuration_cylinder_id bigint, fk_dive_id bigint);
                CREATE TABLE t_dive_configuration_cylinder_usage_window(fk_configuration_cylinder_id bigint, window_start timestamptz, window_end timestamptz);
                CREATE TABLE t_dive_site(pk_dive_site_id bigint);
                INSERT INTO t_dive_profiles VALUES (1, '2026-01-01T10:00:00Z');
                INSERT INTO t_dive_measurements VALUES (1, '2026-01-01T10:00:01.250Z');
                INSERT INTO t_dive_summary VALUES (2, '2026-01-01T10:00:00Z');
                INSERT INTO t_dive_configuration_cylinder VALUES (3, 2);
                INSERT INTO t_dive_configuration_cylinder_usage_window VALUES (3, '2026-01-01T09:59:59.500Z', null);
                """);
            try (final var input =
                    Objects.requireNonNull(
                            getClass()
                                    .getResourceAsStream(
                                            "/db/migration/postgresql/V0_4_17__relative_profile_times.sql"))) {
                statement.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            try (final var rows =
                    statement.executeQuery(
                            "SELECT extract(epoch FROM elapsed) FROM t_dive_measurements")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDouble(1)).isEqualTo(1.25);
            }
            try (final var rows =
                    statement.executeQuery(
                            "SELECT extract(epoch FROM start_offset), end_offset FROM t_dive_configuration_cylinder_usage_window")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getDouble(1)).isEqualTo(-0.5);
                assertThat(rows.getObject(2)).isNull();
            }
        }
    }
}

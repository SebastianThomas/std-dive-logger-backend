package ch.sthomas.stddivelogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * Proves V0_4_3__time_to_surface.sql (adding the two nullable TTS columns) is a real, safe in-place
 * migration - not just "applies to an empty schema" (already covered by {@link
 * FlywayMigrationIntegrationTest}), but "applies without touching or losing pre-existing rows" on a
 * database that already has real dive data from before the column existed, the way a real
 * deployment's upgrade actually works.
 */
@Testcontainers
class TimeToSurfaceMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"));

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @Test
    void preExistingRowsSurviveTheMigrationWithNullTtsAndUnchangedOldColumns() throws Exception {
        final var preMigration =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword())
                        .locations("classpath:db/migration/postgresql")
                        .target(MigrationVersion.fromVersion("0.4.2"))
                        .load();
        preMigration.migrate();

        final long diveId;
        final long profileId;
        try (var conn = connect();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "INSERT INTO t_users (email, password, name, verified, created_at, "
                            + "updated_at) VALUES ('t@example.com', 'x', 'Tester', true, now(),"
                            + " now())");
            stmt.execute(
                    "INSERT INTO t_dive_site (name, location) VALUES ('Test Site', "
                            + "ST_SetSRID(ST_MakePoint(10.0, 45.0), 4326))");
            stmt.execute("INSERT INTO t_computer_manufacturer (name) VALUES ('Suunto')");
            stmt.execute(
                    "INSERT INTO t_dive_computer (fk_user_id, fk_manufacturer_id, "
                            + "serial_number, custom_identifier) VALUES (1, 1, 'SN1', 'EON Core')");
            stmt.execute(
                    "INSERT INTO t_dives (dive_number, dive_site, fk_diver_id) VALUES (1, 1, 1)");
            stmt.execute(
                    "INSERT INTO t_dive_profiles (fk_dive_id, fk_dive_computer, "
                            + "dive_profile_start, dive_profile_end) VALUES (1, 1, "
                            + "'2026-01-01T10:00:00Z', '2026-01-01T10:30:00Z')");
            stmt.execute(
                    "INSERT INTO t_dive_measurements (fk_dive_profile_id, time, depth, "
                            + "temperature_celsius, ndl_minutes) VALUES (1, "
                            + "'2026-01-01T10:05:00Z', 20.5, 18.0, 40)");
            stmt.execute(
                    "INSERT INTO t_dive_summary (fk_dive_id, dive_start, dive_end, max_depth, "
                            + "avg_depth, duration_seconds) VALUES (1, '2026-01-01T10:00:00Z', "
                            + "'2026-01-01T10:30:00Z', 20.5, 15.2, 1800)");

            try (ResultSet rs = stmt.executeQuery("SELECT pk_dive_id FROM t_dives")) {
                rs.next();
                diveId = rs.getLong(1);
            }
            try (ResultSet rs =
                    stmt.executeQuery("SELECT pk_dive_profile_id FROM t_dive_profiles")) {
                rs.next();
                profileId = rs.getLong(1);
            }
        }

        final var fullMigration =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword())
                        .locations("classpath:db/migration/postgresql")
                        .load();
        final var result = fullMigration.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThan(0);

        try (var conn = connect();
                var stmt = conn.createStatement()) {
            try (ResultSet rs =
                    stmt.executeQuery(
                            "SELECT depth, temperature_celsius, ndl_minutes, "
                                    + "time_to_surface_seconds FROM t_dive_measurements "
                                    + "WHERE fk_dive_profile_id = "
                                    + profileId)) {
                assertThat(rs.next()).isTrue();
                // Old data untouched by the ALTER TABLE.
                assertThat(rs.getDouble("depth")).isEqualTo(20.5);
                assertThat(rs.getDouble("temperature_celsius")).isEqualTo(18.0);
                assertThat(rs.getInt("ndl_minutes")).isEqualTo(40);
                // New column defaults to NULL on a pre-existing row, not 0 or an error.
                rs.getObject("time_to_surface_seconds");
                assertThat(rs.wasNull()).isTrue();
            }

            try (ResultSet rs =
                    stmt.executeQuery(
                            "SELECT max_depth, avg_depth, duration_seconds, "
                                    + "max_time_to_surface_seconds FROM t_dive_summary "
                                    + "WHERE fk_dive_id = "
                                    + diveId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("max_depth")).isEqualTo(20.5);
                assertThat(rs.getDouble("avg_depth")).isEqualTo(15.2);
                assertThat(rs.getInt("duration_seconds")).isEqualTo(1800);
                rs.getObject("max_time_to_surface_seconds");
                assertThat(rs.wasNull()).isTrue();
            }

            // The new columns aren't just present - they actually accept and round-trip a real
            // value for rows written after the migration, same table, same connection.
            stmt.execute(
                    "INSERT INTO t_dive_measurements (fk_dive_profile_id, time, depth, "
                            + "time_to_surface_seconds) VALUES ("
                            + profileId
                            + ", '2026-01-01T10:06:00Z', 21.0, 532)");
            stmt.execute(
                    "UPDATE t_dive_summary SET max_time_to_surface_seconds = 532 WHERE "
                            + "fk_dive_id = "
                            + diveId);

            try (ResultSet rs =
                    stmt.executeQuery(
                            "SELECT time_to_surface_seconds FROM t_dive_measurements WHERE "
                                    + "depth = 21.0")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("time_to_surface_seconds")).isEqualTo(532);
            }
            try (ResultSet rs =
                    stmt.executeQuery(
                            "SELECT max_time_to_surface_seconds FROM t_dive_summary WHERE "
                                    + "fk_dive_id = "
                                    + diveId)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("max_time_to_surface_seconds")).isEqualTo(532);
            }
        }
    }
}

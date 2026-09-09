package ch.sthomas.stddivelogger.data;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.service.MapsBoundaryDataService;
import ch.sthomas.stddivelogger.data.service.MapsImportRunStore;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Objects;

@Testcontainers
@ResourceLock("postgres-testcontainer")
class MapsFlywayMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @Test
    void mapsAndPublicMigrationsHaveIndependentRepeatableHistories() {
        final var publicFlyway = flyway("public", "classpath:db/migration/postgresql");
        final var mapsFlyway = flyway("maps", "classpath:db/migration/maps");

        assertThat(publicFlyway.migrate().success).isTrue();
        assertThat(mapsFlyway.migrate().success).isTrue();
        assertThat(mapsFlyway.migrate().migrationsExecuted).isZero();

        assertThat(mapsFlyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(publicFlyway.info().current().getVersion().getVersion()).startsWith("0.4.");
    }

    @Test
    void resolvesCountryAndRegionAndKeepsOffshoreAsAValidResult() {
        flyway("public", "classpath:db/migration/postgresql").migrate();
        flyway("maps", "classpath:db/migration/maps").migrate();
        final var dataSource =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        final var jdbc = new JdbcTemplate(dataSource);
        final var namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        final var service = new MapsBoundaryDataService(namedJdbc);
        final var runStore = new MapsImportRunStore(namedJdbc);

        final long importId =
                Objects.requireNonNull(
                        jdbc.queryForObject(
                                """
                        INSERT INTO maps.import_run
                            (source_url, kubernetes_job_name, status, state, finished_at)
                        VALUES ('test://boundaries', 'maps-test-1', 'SUCCEEDED', 'test-state', now())
                        RETURNING pk_import_run_id
                                """,
                                Long.class));
        jdbc.update(
                """
                INSERT INTO maps.admin_boundary
                    (osm_relation_id, admin_level, name, iso3166_1, geometry, fk_import_run_id)
                VALUES
                    (100, 2, 'Testland', 'TL',
                     ST_GeomFromText('MULTIPOLYGON(((0 0,10 0,10 10,0 10,0 0)))', 4326), ?),
                    (200, 4, 'North Testland', NULL,
                     ST_GeomFromText('MULTIPOLYGON(((0 5,10 5,10 10,0 10,0 5)))', 4326), ?)
                """,
                importId,
                importId);
        final long inlandSite = insertSite(jdbc, "Inland", 7, 7);
        final long offshoreSite = insertSite(jdbc, "Offshore", 30, 30);

        final var inland = service.resolveSite(inlandSite).orElseThrow();
        final var offshore = service.resolveSite(offshoreSite).orElseThrow();

        assertThat(inland.countryName()).isEqualTo("Testland");
        assertThat(inland.countryCode()).isEqualTo("TL");
        assertThat(inland.regionName()).isEqualTo("North Testland");
        assertThat(inland.status()).isEqualTo("RESOLVED");
        assertThat(inland.boundaryImportVersion()).isEqualTo(importId);
        assertThat(offshore.countryName()).isNull();
        assertThat(offshore.status()).isEqualTo("NO_COUNTRY");
        assertThat(runStore.latestSuccessfulState()).isEqualTo("test-state");
    }

    private static long insertSite(
            final JdbcTemplate jdbc,
            final String name,
            final double longitude,
            final double latitude) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        """
                INSERT INTO public.t_dive_site (name, location)
                VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
                RETURNING pk_dive_site_id
                """,
                        Long.class,
                        name,
                        longitude,
                        latitude));
    }

    private static Flyway flyway(final String schema, final String location) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations(location)
                .load();
    }
}

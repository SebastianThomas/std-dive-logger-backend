package ch.sthomas.stddivelogger.analytics.maps;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"scheduling.enabled=false"})
@Testcontainers
@ActiveProfiles("local-output")
class MapsDataSourceContextIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(final DynamicPropertyRegistry registry) {
        registry.add("maps.datasource.url", postgres::getJdbcUrl);
        registry.add("maps.datasource.username", postgres::getUsername);
        registry.add("maps.datasource.password", postgres::getPassword);
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired DataSource primaryDataSource;

    @Autowired
    @Qualifier("mapsDataSource")
    DataSource mapsDataSource;

    @Autowired
    @Qualifier("mapsJdbcTemplate")
    JdbcTemplate mapsJdbc;

    @Autowired
    @Qualifier("mapsNamedParameterJdbcTemplate")
    NamedParameterJdbcTemplate mapsNamedJdbc;

    @Autowired
    @Qualifier("mapsFlyway")
    Flyway mapsFlyway;

    @Test
    void mapsBeansRemainSeparateFromThePrimaryJpaDataSource() {
        assertThat(mapsDataSource).isNotSameAs(primaryDataSource);
        assertThat(mapsJdbc.getDataSource()).isSameAs(mapsDataSource);
        assertThat(mapsNamedJdbc.getJdbcTemplate().getDataSource()).isSameAs(mapsDataSource);
        assertThat(mapsFlyway.getConfiguration().getDefaultSchema()).isEqualTo("maps");
        assertThat(
                        mapsJdbc.queryForObject(
                                "SELECT count(*) FROM maps.flyway_schema_history", Integer.class))
                .isPositive();
    }
}

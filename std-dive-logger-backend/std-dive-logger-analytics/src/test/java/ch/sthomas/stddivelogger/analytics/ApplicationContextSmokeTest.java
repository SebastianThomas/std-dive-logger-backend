package ch.sthomas.stddivelogger.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Boots the real application context against a real Postgres - if this fails, the app won't start
 * in any real environment either. Exists specifically to catch classpath conflicts (e.g. the
 * classic-Jackson-2 version skew that used to break Hibernate's JacksonJsonFormatMapper and
 * Spring's YAML message converter autoconfiguration at startup) that a narrower test wouldn't.
 */
@SpringBootTest(properties = "scheduling.enabled=false")
@Testcontainers
@ActiveProfiles("local-output")
class ApplicationContextSmokeTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Test
    void contextLoads() {
        // Intentionally empty - success is the context loading without exceptions.
    }
}

package ch.sthomas.stddivelogger.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@AutoConfigureMetrics
@Import(HttpObservabilityIntegrationTest.Endpoints.class)
@Testcontainers
class HttpObservabilityIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @DynamicPropertySource
    static void nonDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-secret",
                () -> "observability-test-secret-at-least-32-bytes");
        registry.add(
                "ch.sthomas.stddivelogger.ws.jwt-refresh-secret",
                () -> "http-message-not-readable-it-jwt-refresh-secret-needs-to-be-long-enough");
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.bucket", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.account-id", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.access-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.storage.r2.secret-key", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.address", () -> "test@test.ch");
        registry.add("ch.sthomas.stddivelogger.email.password", () -> "unused");
        registry.add("ch.sthomas.stddivelogger.email.host", () -> "localhost");
    }

    @Autowired private RestTestClient client;
    @Autowired private PrometheusMeterRegistry metrics;

    @Test
    void actualServerErrorsPublishStatusHistogramAndTailPercentiles() {
        client.get()
                .uri("/observability-test/failure")
                .exchange()
                .expectStatus()
                .is5xxServerError();
        final var timer = metrics.get("http.server.requests").tag("status", "503").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("outcome")).isEqualTo("SERVER_ERROR");
        assertThat(timer.getId().getTag("uri")).isEqualTo("/observability-test/failure");
        assertThat(timer.getId().getTag("application")).isEqualTo("std-dive-logger-ws-local");
        final var scrape = metrics.scrape();
        assertThat(scrape)
                .contains(
                        "http_server_requests_seconds_bucket",
                        "status=\"503\"",
                        "logback_events_total");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Endpoints {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping("/observability-test/failure")
        ResponseEntity<Void> failure() {
            return ResponseEntity.status(503).build();
        }
    }
}

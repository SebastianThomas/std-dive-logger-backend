package ch.sthomas.stddivelogger.analytics.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import ch.sthomas.stddivelogger.analytics.job.AnalyticsJobQueue;
import ch.sthomas.stddivelogger.analytics.job.JobKind;
import ch.sthomas.stddivelogger.analytics.services.AnalyticsService;
import ch.sthomas.stddivelogger.data.service.AnalyticsJobRunStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "scheduling.enabled=false")
@AutoConfigureRestTestClient
@Testcontainers
@ActiveProfiles("local-output")
class AnalyticsDashboardIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:18-3.6")
                            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivelogger.storage.r2.base-url", () -> "http://localhost/unused");
    }

    @Autowired AnalyticsJobQueue queue;
    @Autowired AnalyticsJobRunStore runs;
    @Autowired AnalyticsDashboardController controller;
    @Autowired RestTestClient client;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @MockitoBean AnalyticsService analytics;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM t_analytics_job_run");
        reset(analytics);
    }

    @Test
    void manualAndScheduledRunsShareDurableDeduplicatedQueue() throws Exception {
        assertThat(queue.enqueue(JobKind.SUMMARIES, true)).isTrue();
        assertThat(queue.enqueue(JobKind.SUMMARIES, false)).isFalse();
        assertThat(runs.recent().getFirst().status()).isEqualTo("QUEUED");
        queue.processNext();
        verify(analytics).computeDiveSummaries();
        final var finished = runs.recent().getFirst();
        assertThat(finished.status()).isEqualTo("SUCCEEDED");
        assertThat(finished.trigger()).isEqualTo("MANUAL");
        assertThat(finished.startedAt()).isNotNull();
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(queue.enqueue(JobKind.SUMMARIES, false)).isTrue();
    }

    @Test
    void failuresAreRecordedWithoutReturningSensitiveMessages() throws Exception {
        doThrow(new IllegalStateException("secret-data")).when(analytics).refreshDiveSiteStats();
        queue.enqueue(JobKind.SITES, true);
        queue.processNext();
        assertThat(runs.recent().getFirst().status()).isEqualTo("FAILED");
        assertThat(runs.recent().getFirst().errorType()).isEqualTo("IllegalStateException");
        assertThat(queue.enqueue(JobKind.SITES, true)).isTrue();
    }

    @Test
    void workerHonorsOtherReplicaLockAndRecoversInterruptedRun() throws Exception {
        queue.enqueue(JobKind.ACTIVITY, false);
        try (final var connection = dataSource.getConnection();
                final var statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(734268193)");
            try {
                queue.processNext();
                assertThat(runs.recent().getFirst().status()).isEqualTo("QUEUED");
            } finally {
                statement.execute("SELECT pg_advisory_unlock(734268193)");
            }
        }
        jdbc.update("UPDATE t_analytics_job_run SET status='RUNNING', started_at=now()");
        queue.processNext();
        assertThat(runs.recent().getFirst().status()).isEqualTo("INTERRUPTED");
    }

    @Test
    void dashboardExposesFutureSchedulesAndProtectsManualActions() {
        final var status = controller.status();
        assertThat(status.jobs()).hasSize(7);
        assertThat(status.jobs())
                .allSatisfy(job -> assertThat(job.nextRun()).isAfter(status.now()));
        client.get().uri("/ops").exchange().expectStatus().isOk();
        client.get().uri("/ops/app.js").exchange().expectStatus().isOk();
        client.get().uri("/ops/style.css").exchange().expectStatus().isOk();
        client.get().uri("/ops/api/status").exchange().expectStatus().isOk();
        client.post().uri("/ops/api/jobs/SUMMARIES/runs").exchange().expectStatus().isForbidden();
        assertThat(runs.recent()).isEmpty();
    }

    @Test
    void manualControllerReturnsAcceptedThenConflict() {
        final var enabled = new AnalyticsDashboardController(queue, runs, true);
        assertThat(enabled.start(JobKind.CLEANUP).getStatusCode().value()).isEqualTo(202);
        assertThat(enabled.start(JobKind.CLEANUP).getStatusCode().value()).isEqualTo(409);
    }
}

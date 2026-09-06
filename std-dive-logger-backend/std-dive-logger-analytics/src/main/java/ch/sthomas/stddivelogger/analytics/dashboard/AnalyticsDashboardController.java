package ch.sthomas.stddivelogger.analytics.dashboard;

import ch.sthomas.stddivelogger.analytics.job.AnalyticsJobQueue;
import ch.sthomas.stddivelogger.analytics.job.JobKind;
import ch.sthomas.stddivelogger.data.service.AnalyticsJobRunStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class AnalyticsDashboardController {
    private final AnalyticsJobQueue queue;
    private final AnalyticsJobRunStore runs;
    private final boolean schedulingEnabled;
    private final Instant startup = Instant.now();

    public AnalyticsDashboardController(
            AnalyticsJobQueue queue,
            AnalyticsJobRunStore runs,
            @Value("${scheduling.enabled:true}") boolean schedulingEnabled) {
        this.queue = queue;
        this.runs = runs;
        this.schedulingEnabled = schedulingEnabled;
    }

    @GetMapping(
            value = {"/ops", "/ops/"},
            produces = "text/html")
    Resource page() {
        return new ClassPathResource("dashboard/index.html");
    }

    @GetMapping(value = "/ops/app.js", produces = "application/javascript")
    Resource script() {
        return new ClassPathResource("dashboard/app.js");
    }

    @GetMapping(value = "/ops/style.css", produces = "text/css")
    Resource style() {
        return new ClassPathResource("dashboard/style.css");
    }

    @GetMapping("/ops/api/csrf")
    Map<String, String> csrf(final CsrfToken token) {
        return Map.of("token", token.getToken(), "header", token.getHeaderName());
    }

    @GetMapping("/ops/api/status")
    Status status() {
        final var now = Instant.now();
        final var jobs =
                Arrays.stream(JobKind.values())
                        .map(
                                job -> {
                                    var next =
                                            Objects.requireNonNull(
                                                            CronExpression.parse(job.cron)
                                                                    .next(
                                                                            now.atZone(
                                                                                    ZoneOffset
                                                                                            .UTC)))
                                                    .toInstant();
                                    final var first = startup.plusSeconds(job.startupDelaySeconds);
                                    if (job.startupDelaySeconds > 0
                                            && first.isAfter(now)
                                            && first.isBefore(next)) next = first;
                                    return new Job(
                                            job.name(), job.label, job.description, job.cron, next);
                                })
                        .toList();
        return new Status(now, schedulingEnabled, jobs, runs.recent());
    }

    @PostMapping("/ops/api/jobs/{job}/runs")
    ResponseEntity<Map<String, String>> start(@PathVariable final JobKind job) {
        if (!schedulingEnabled)
            return ResponseEntity.status(503)
                    .body(Map.of("message", "The job worker is disabled."));
        return queue.enqueue(job, true)
                ? ResponseEntity.accepted().body(Map.of("message", "Run queued."))
                : ResponseEntity.status(409)
                        .body(Map.of("message", "This job is already queued or running."));
    }

    public record Job(String id, String label, String description, String cron, Instant nextRun) {}

    public record Status(
            Instant now,
            boolean schedulingEnabled,
            List<Job> jobs,
            List<AnalyticsJobRunStore.Run> runs) {}
}

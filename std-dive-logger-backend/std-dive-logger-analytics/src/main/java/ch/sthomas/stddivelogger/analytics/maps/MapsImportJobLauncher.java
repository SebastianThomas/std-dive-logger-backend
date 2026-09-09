package ch.sthomas.stddivelogger.analytics.maps;

import ch.sthomas.stddivelogger.data.service.MapsImportRunStore;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = {"maps.enabled", "maps.import.kubernetes.enabled"},
        havingValue = "true")
public class MapsImportJobLauncher {
    private static final Logger LOG = LoggerFactory.getLogger(MapsImportJobLauncher.class);
    private static final DateTimeFormatter NAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final KubernetesClient kubernetes;
    private final MapsImportProperties properties;
    private final MapsImportJobFactory factory;
    private final MapsImportStateCalculator stateCalculator;
    private final MapsImportRunStore runs;

    public MapsImportJobLauncher(
            final KubernetesClient kubernetes,
            final MapsImportProperties properties,
            final MapsImportJobFactory factory,
            final MapsImportStateCalculator stateCalculator,
            final MapsImportRunStore runs) {
        this.kubernetes = kubernetes;
        this.properties = properties;
        this.factory = factory;
        this.stateCalculator = stateCalculator;
        this.runs = runs;
    }

    public synchronized long launchConfiguredImport() {
        return launchConfiguredImport(stateCalculator.calculate());
    }

    public synchronized boolean launchIfStateChanged() {
        final String state = stateCalculator.calculate();
        if (state.equals(runs.latestSuccessfulState()) || hasActiveImport()) return false;
        launchConfiguredImport(state);
        return true;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void launchChangedImportOnStartup() {
        try {
            if (launchIfStateChanged()) {
                LOG.info("Maps import configuration state changed; launched a replacement import");
            }
        } catch (RuntimeException exception) {
            // Import orchestration must never make the analytics service or the last-known-good
            // boundary data unavailable.
            LOG.error("Could not evaluate or launch the startup maps import", exception);
        }
    }

    private long launchConfiguredImport(final String state) {
        if (hasActiveImport()) {
            throw new IllegalStateException("A maps import Kubernetes Job is already active");
        }

        final Instant sourceTimestamp = Instant.now();
        final String jobName =
                "maps-import-"
                        + NAME_TIME.format(sourceTimestamp)
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 6);
        final String checksum = properties.getSourceChecksum().trim();
        final long runId =
                runs.plan(
                        properties.getSourceUrl(),
                        checksum.isEmpty() ? null : checksum,
                        sourceTimestamp,
                        jobName,
                        state);
        final Job job =
                factory.create(
                        jobName,
                        runId,
                        properties.getSourceUrl(),
                        checksum,
                        sourceTimestamp,
                        properties.getDatabaseSecretName());
        try {
            kubernetes
                    .batch()
                    .v1()
                    .jobs()
                    .inNamespace(properties.getNamespace())
                    .resource(job)
                    .create();
            runs.markSubmitted(runId);
            return runId;
        } catch (RuntimeException exception) {
            runs.markFailed(runId, exception.getClass().getSimpleName());
            throw exception;
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void reconcile() {
        kubernetes
                .batch()
                .v1()
                .jobs()
                .inNamespace(properties.getNamespace())
                .withLabels(MapsImportJobFactory.LABELS)
                .list()
                .getItems()
                .forEach(this::reconcile);
    }

    private boolean hasActiveImport() {
        return kubernetes
                .batch()
                .v1()
                .jobs()
                .inNamespace(properties.getNamespace())
                .withLabels(MapsImportJobFactory.LABELS)
                .list()
                .getItems()
                .stream()
                .anyMatch(MapsImportJobLauncher::isActive);
    }

    private void reconcile(final Job job) {
        final String runIdValue =
                job.getMetadata().getAnnotations().get("std-dive-logger/import-run-id");
        if (runIdValue == null) return;
        final long runId = Long.parseLong(runIdValue);
        if (hasTrueCondition(job, "Failed")) {
            runs.markFailed(runId, "KubernetesJobFailed");
        } else if (isActive(job)) {
            runs.markRunning(runId);
        }
        // A successful promotion updates maps.import_run in the same transaction as the boundary
        // swap. The reconciler deliberately does not claim success independently of that commit.
    }

    private static boolean isActive(final Job job) {
        return !hasTrueCondition(job, "Complete") && !hasTrueCondition(job, "Failed");
    }

    private static boolean hasTrueCondition(final Job job, final String type) {
        return job.getStatus() != null
                && job.getStatus().getConditions() != null
                && job.getStatus().getConditions().stream()
                        .anyMatch(
                                condition ->
                                        type.equals(condition.getType())
                                                && "True".equals(condition.getStatus()));
    }
}

package ch.sthomas.stddivelogger.analytics.maps;

import ch.sthomas.stddivelogger.data.service.MapsImportKind;
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
import java.util.Locale;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "maps.import.kubernetes.enabled", havingValue = "true")
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

    /** Launches an import for every boundary source that has no active Job. */
    public synchronized void launchConfiguredImport() {
        for (final MapsImportKind kind : MapsImportKind.values()) {
            if (hasActiveImport(kind)) continue;
            launchConfiguredImport(kind, stateCalculator.calculate(kind));
        }
    }

    public synchronized boolean launchIfStateChanged() {
        boolean launched = false;
        for (final MapsImportKind kind : MapsImportKind.values()) {
            final String state = stateCalculator.calculate(kind);
            if (state.equals(runs.latestSuccessfulState(kind)) || hasActiveImport(kind)) continue;
            launchConfiguredImport(kind, state);
            launched = true;
        }
        return launched;
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

    private long launchConfiguredImport(final MapsImportKind kind, final String state) {
        if (hasActiveImport(kind)) {
            throw new IllegalStateException(
                    "A " + kind + " boundary import Kubernetes Job is already active");
        }

        final Instant sourceTimestamp = Instant.now();
        final String jobName =
                "maps-import-"
                        + kind.name().toLowerCase(Locale.ROOT)
                        + "-"
                        + NAME_TIME.format(sourceTimestamp)
                        + "-"
                        + UUID.randomUUID().toString().substring(0, 6);
        final String checksum = properties.getSourceChecksum().trim();
        final long runId =
                runs.plan(
                        kind,
                        sourceUrl(kind),
                        kind == MapsImportKind.OSM && !checksum.isEmpty() ? checksum : null,
                        sourceTimestamp,
                        jobName,
                        state);
        final Job job =
                switch (kind) {
                    case OSM ->
                            factory.createOsmImport(
                                    jobName,
                                    runId,
                                    properties.getSourceUrl(),
                                    checksum,
                                    properties.getDatabaseSecretName());
                    case CGAZ ->
                            factory.createCgazImport(
                                    jobName,
                                    runId,
                                    properties.getCgazAdm0Url(),
                                    properties.getCgazAdm1Url(),
                                    properties.getDatabaseSecretName());
                };
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

    /** CGAZ downloads both levels in one Job, so both URLs together are the run's source. */
    private String sourceUrl(final MapsImportKind kind) {
        return switch (kind) {
            case OSM -> properties.getSourceUrl();
            case CGAZ -> properties.getCgazAdm0Url() + " " + properties.getCgazAdm1Url();
        };
    }

    private boolean hasActiveImport(final MapsImportKind kind) {
        return kubernetes
                .batch()
                .v1()
                .jobs()
                .inNamespace(properties.getNamespace())
                .withLabels(MapsImportJobFactory.LABELS)
                .withLabel(MapsImportJobFactory.KIND_LABEL, kind.name())
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
        } else {
            promote(runId, kindOf(job));
        }
    }

    /**
     * Promotes the staged boundaries of a completed Job. The promotion is idempotent, so the
     * repeated reconciliations of a completed Job that lingers until its TTL are no-ops.
     */
    private void promote(final long runId, final MapsImportKind kind) {
        try {
            if (runs.promote(runId, kind)) {
                LOG.info("Promoted the staged {} boundaries of maps import run {}", kind, runId);
            }
        } catch (RuntimeException exception) {
            LOG.error(
                    "Could not promote the staged boundaries of maps import run {}",
                    runId,
                    exception);
            runs.markFailed(runId, "BoundaryPromotionFailed");
        }
    }

    /**
     * Jobs from before the CGAZ source existed carry no source label and are all osm2pgsql runs.
     */
    private static MapsImportKind kindOf(final Job job) {
        final String label = job.getMetadata().getLabels().get(MapsImportJobFactory.KIND_LABEL);
        return label == null ? MapsImportKind.OSM : MapsImportKind.valueOf(label);
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

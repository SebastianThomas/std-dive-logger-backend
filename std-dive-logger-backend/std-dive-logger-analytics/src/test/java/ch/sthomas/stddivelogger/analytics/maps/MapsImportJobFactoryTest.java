package ch.sthomas.stddivelogger.analytics.maps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Instant;

class MapsImportJobFactoryTest {

    @Test
    void buildsLeastPrivilegeResourceBoundImportJob() {
        final var job =
                new MapsImportJobFactory()
                        .create(
                                "maps-import-test",
                                42,
                                "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf",
                                "abc123",
                                Instant.parse("2026-09-09T12:00:00Z"),
                                "maps-db");
        final var spec = job.getSpec();
        final var pod = spec.getTemplate().getSpec();

        assertThat(job.getMetadata().getLabels()).containsAllEntriesOf(MapsImportJobFactory.LABELS);
        assertThat(spec.getBackoffLimit()).isEqualTo(1);
        assertThat(spec.getActiveDeadlineSeconds()).isEqualTo(7200L);
        assertThat(spec.getTtlSecondsAfterFinished()).isEqualTo(86400);
        assertThat(pod.getServiceAccountName()).isEqualTo("std-dive-logger-osm-importer");
        assertThat(pod.getAutomountServiceAccountToken()).isFalse();
        assertThat(pod.getInitContainers())
                .extracting("name")
                .containsExactly("download", "import-boundaries");
        assertThat(pod.getInitContainers().get(1).getImage())
                .isEqualTo(MapsImportJobFactory.OSM2PGSQL_IMAGE);
        assertThat(pod.getInitContainers().getFirst().getImage())
                .isEqualTo(MapsImportJobFactory.DOWNLOAD_IMAGE);
        assertThat(pod.getContainers()).extracting("name").containsExactly("promote-boundaries");
        assertThat(pod.getContainers().getFirst().getImage())
                .isEqualTo(MapsImportJobFactory.PROMOTION_IMAGE);
        assertThat(pod.getInitContainers().get(1).getResources().getLimits())
                .containsKeys("cpu", "memory", "ephemeral-storage");
        assertThat(
                        pod.getInitContainers()
                                .get(1)
                                .getEnv()
                                .getFirst()
                                .getValueFrom()
                                .getSecretKeyRef()
                                .getName())
                .isEqualTo("maps-db");
        assertThat(
                        pod.getInitContainers()
                                .get(1)
                                .getEnv()
                                .getFirst()
                                .getValueFrom()
                                .getSecretKeyRef()
                                .getKey())
                .isEqualTo("uri");
    }
}

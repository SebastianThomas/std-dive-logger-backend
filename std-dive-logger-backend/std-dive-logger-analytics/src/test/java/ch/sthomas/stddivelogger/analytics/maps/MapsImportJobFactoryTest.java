package ch.sthomas.stddivelogger.analytics.maps;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.service.MapsImportKind;

import org.junit.jupiter.api.Test;

class MapsImportJobFactoryTest {

    @Test
    void buildsLeastPrivilegeResourceBoundImportJob() {
        final var job =
                new MapsImportJobFactory()
                        .createOsmImport(
                                "maps-import-test",
                                42,
                                "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf",
                                "abc123",
                                "maps-db");
        final var spec = job.getSpec();
        final var pod = spec.getTemplate().getSpec();

        assertThat(job.getMetadata().getLabels()).containsAllEntriesOf(MapsImportJobFactory.LABELS);
        assertThat(spec.getBackoffLimit()).isEqualTo(1);
        assertThat(spec.getActiveDeadlineSeconds()).isEqualTo(7200L);
        assertThat(spec.getTtlSecondsAfterFinished()).isEqualTo(86400);
        assertThat(pod.getServiceAccountName()).isEqualTo("std-dive-logger-osm-importer");
        assertThat(pod.getAutomountServiceAccountToken()).isFalse();
        assertThat(pod.getInitContainers()).isEmpty();
        assertThat(pod.getContainers()).extracting("name").containsExactly("import-boundaries");
        final var container = pod.getContainers().getFirst();
        assertThat(container.getImage()).isEqualTo(MapsImportJobFactory.OSM2PGSQL_IMAGE);
        assertThat(container.getArgs().getFirst()).contains("curl -fL", "exec osm2pgsql");
        assertThat(container.getResources().getLimits())
                .containsKeys("cpu", "memory", "ephemeral-storage");
        assertThat(container.getEnv().getFirst().getValueFrom().getSecretKeyRef().getName())
                .isEqualTo("maps-db");
        assertThat(container.getEnv().getFirst().getValueFrom().getSecretKeyRef().getKey())
                .isEqualTo("uri");
        assertThat(job.getMetadata().getLabels())
                .containsEntry(MapsImportJobFactory.KIND_LABEL, MapsImportKind.OSM.name());
    }

    @Test
    void buildsSingleContainerCgazImportJob() {
        final var job =
                new MapsImportJobFactory()
                        .createCgazImport(
                                "maps-import-cgaz-test",
                                43,
                                "https://example.test/ADM0.gpkg",
                                "https://example.test/ADM1.gpkg",
                                "maps-db");
        final var pod = job.getSpec().getTemplate().getSpec();

        assertThat(job.getMetadata().getLabels())
                .containsAllEntriesOf(MapsImportJobFactory.LABELS)
                .containsEntry(MapsImportJobFactory.KIND_LABEL, MapsImportKind.CGAZ.name());
        assertThat(pod.getInitContainers()).isEmpty();
        assertThat(pod.getContainers()).extracting("name").containsExactly("import-boundaries");
        final var container = pod.getContainers().getFirst();
        assertThat(container.getImage()).isEqualTo(MapsImportJobFactory.GDAL_IMAGE);
        assertThat(container.getArgs().getFirst())
                .contains(
                        "wget -q -O /work/adm0.gpkg",
                        "load /work/adm1.gpkg globalADM1 adm1",
                        "-a_srs EPSG:4326",
                        "-lco SCHEMA=maps_cgaz_stage");
        assertThat(container.getEnv())
                .extracting("name")
                .containsExactly("DATABASE_URL", "ADM0_URL", "ADM1_URL");
    }
}

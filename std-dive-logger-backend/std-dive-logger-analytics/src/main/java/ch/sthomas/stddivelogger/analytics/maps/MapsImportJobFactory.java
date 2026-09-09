package ch.sthomas.stddivelogger.analytics.maps;

import ch.sthomas.stddivelogger.data.service.MapsImportKind;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MapsImportJobFactory {
    /** Ships {@code curl} and {@code sha256sum}, so the download needs no second container. */
    public static final String OSM2PGSQL_IMAGE =
            "docker.io/iboates/osm2pgsql@sha256:d1bd9038fdd6c1526abd31aa60342d2975067bb570cf101b66bdc411762bf81b";

    /** Ships {@code ogr2ogr} with the PostgreSQL driver, which the alpine-small variant lacks. */
    public static final String GDAL_IMAGE =
            "ghcr.io/osgeo/gdal@sha256:d49f0d254bea0aa53bad4f98eab6737c3c73a9fb81fc1a2f681b538a48824ef3";

    /** Selects every boundary import Job, regardless of its source. */
    public static final Map<String, String> LABELS =
            Map.of(
                    "app.kubernetes.io/name", "osm2pgsql-import",
                    "std-dive-logger/job-kind", "maps-import");

    public static final String KIND_LABEL = "std-dive-logger/import-source";

    private static final VolumeMount WORK_MOUNT =
            new VolumeMountBuilder().withName("work").withMountPath("/work").build();
    private static final VolumeMount CONFIG_MOUNT =
            new VolumeMountBuilder()
                    .withName("config")
                    .withMountPath("/config")
                    .withReadOnly(true)
                    .build();

    public Job createOsmImport(
            final String jobName,
            final long runId,
            final String sourceUrl,
            final String sourceChecksum,
            final String databaseSecretName) {
        final var container =
                new ContainerBuilder()
                        .withName("import-boundaries")
                        .withImage(OSM2PGSQL_IMAGE)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(
                                "set -eu; curl -fL --retry 3 -o /work/source.osm.pbf \"$SOURCE_URL\"; "
                                        + "if [ -n \"$SOURCE_SHA256\" ]; then echo \"$SOURCE_SHA256  /work/source.osm.pbf\" | sha256sum -c -; fi; "
                                        + "exec osm2pgsql --create --slim --drop --output=flex "
                                        + "--style=/config/boundaries.lua --schema=maps_osm_stage "
                                        + "--middle-schema=maps_osm_stage --database=\"$DATABASE_URL\" "
                                        + "/work/source.osm.pbf")
                        .withEnv(
                                secretEnv("DATABASE_URL", databaseSecretName, "uri"),
                                env("SOURCE_URL", sourceUrl),
                                env("SOURCE_SHA256", sourceChecksum))
                        .withVolumeMounts(WORK_MOUNT, CONFIG_MOUNT)
                        .withResources(resources("500m", "1Gi", "4", "6Gi", "8Gi"))
                        .build();
        return job(
                MapsImportKind.OSM, jobName, runId, container, workVolume("8Gi"), configVolume());
    }

    public Job createCgazImport(
            final String jobName,
            final long runId,
            final String adm0Url,
            final String adm1Url,
            final String databaseSecretName) {
        // The GeoPackages declare an undefined SRS although their coordinates are WGS 84 degrees,
        // so the CRS has to be assigned rather than reprojected.
        final String load =
                "ogr2ogr -f PostgreSQL \"PG:$DATABASE_URL\" \"$1\" \"$2\" -a_srs EPSG:4326 "
                        + "-nlt MULTIPOLYGON -nln \"$3\" -lco SCHEMA=maps_cgaz_stage "
                        + "-lco GEOMETRY_NAME=geometry -lco SPATIAL_INDEX=NONE -overwrite "
                        + "--config PG_USE_COPY YES";
        final var container =
                new ContainerBuilder()
                        .withName("import-boundaries")
                        .withImage(GDAL_IMAGE)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(
                                "set -eu; "
                                        + "load() { "
                                        + load
                                        + "; }; "
                                        + "wget -q -O /work/adm0.gpkg \"$ADM0_URL\"; "
                                        + "wget -q -O /work/adm1.gpkg \"$ADM1_URL\"; "
                                        + "load /work/adm0.gpkg globalADM0 adm0; "
                                        + "load /work/adm1.gpkg globalADM1 adm1")
                        .withEnv(
                                secretEnv("DATABASE_URL", databaseSecretName, "uri"),
                                env("ADM0_URL", adm0Url),
                                env("ADM1_URL", adm1Url))
                        .withVolumeMounts(WORK_MOUNT)
                        .withResources(resources("250m", "512Mi", "2", "2Gi", "2Gi"))
                        .build();
        return job(MapsImportKind.CGAZ, jobName, runId, container, workVolume("2Gi"));
    }

    private static Job job(
            final MapsImportKind kind,
            final String jobName,
            final long runId,
            final Container container,
            final Volume... volumes) {
        final Map<String, String> labels = new HashMap<>(LABELS);
        labels.put(KIND_LABEL, kind.name());
        final Map<String, String> annotations =
                Map.of("std-dive-logger/import-run-id", Long.toString(runId));
        // The staged boundaries are promoted by MapsImportRunStore#promote once this Job completes,
        // which keeps every Job to a single container and the promotion in one transaction.
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(1)
                .withActiveDeadlineSeconds(7200L)
                .withTtlSecondsAfterFinished(86400)
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withServiceAccountName("std-dive-logger-osm-importer")
                .withAutomountServiceAccountToken(false)
                .withContainers(container)
                .withVolumes(List.of(volumes))
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private static Volume workVolume(final String sizeLimit) {
        return new VolumeBuilder()
                .withName("work")
                .withEmptyDir(
                        new EmptyDirVolumeSourceBuilder()
                                .withSizeLimit(new Quantity(sizeLimit))
                                .build())
                .build();
    }

    private static Volume configVolume() {
        return new VolumeBuilder()
                .withName("config")
                .withConfigMap(
                        new ConfigMapVolumeSourceBuilder()
                                .withName("std-dive-logger-osm-import")
                                .build())
                .build();
    }

    private static EnvVar env(final String name, final String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private static EnvVar secretEnv(final String name, final String secretName, final String key) {
        return new EnvVarBuilder()
                .withName(name)
                .withValueFrom(
                        new EnvVarSourceBuilder()
                                .withSecretKeyRef(
                                        new SecretKeySelectorBuilder()
                                                .withName(secretName)
                                                .withKey(key)
                                                .build())
                                .build())
                .build();
    }

    private static ResourceRequirements resources(
            final String requestCpu,
            final String requestMemory,
            final String limitCpu,
            final String limitMemory,
            final String ephemeralStorage) {
        return new ResourceRequirementsBuilder()
                .addToRequests("cpu", new Quantity(requestCpu))
                .addToRequests("memory", new Quantity(requestMemory))
                .addToRequests("ephemeral-storage", new Quantity(ephemeralStorage))
                .addToLimits("cpu", new Quantity(limitCpu))
                .addToLimits("memory", new Quantity(limitMemory))
                .addToLimits("ephemeral-storage", new Quantity(ephemeralStorage))
                .build();
    }
}

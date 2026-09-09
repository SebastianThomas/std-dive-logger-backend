package ch.sthomas.stddivelogger.analytics.maps;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class MapsImportJobFactory {
    public static final String DOWNLOAD_IMAGE = "docker.io/curlimages/curl:8.16.0";
    public static final String OSM2PGSQL_IMAGE =
            "docker.io/iboates/osm2pgsql@sha256:d1bd9038fdd6c1526abd31aa60342d2975067bb570cf101b66bdc411762bf81b";
    public static final String PROMOTION_IMAGE = "docker.io/postgres:18-alpine";
    public static final Map<String, String> LABELS =
            Map.of(
                    "app.kubernetes.io/name", "osm2pgsql-import",
                    "std-dive-logger/job-kind", "maps-import");

    public Job create(
            final String jobName,
            final long runId,
            final String sourceUrl,
            final String sourceChecksum,
            final Instant sourceTimestamp,
            final String databaseSecretName) {
        final var workMount =
                new VolumeMountBuilder().withName("work").withMountPath("/work").build();
        final var configMount =
                new VolumeMountBuilder()
                        .withName("config")
                        .withMountPath("/config")
                        .withReadOnly(true)
                        .build();
        final var databaseUrl = secretEnv("DATABASE_URL", databaseSecretName, "uri");

        final var download =
                new ContainerBuilder()
                        .withName("download")
                        .withImage(DOWNLOAD_IMAGE)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(
                                "set -eu; curl -fL --retry 3 -o /work/source.osm.pbf \"$SOURCE_URL\"; "
                                        + "if [ -n \"$SOURCE_SHA256\" ]; then echo \"$SOURCE_SHA256  /work/source.osm.pbf\" | sha256sum -c -; fi")
                        .withEnv(
                                new EnvVarBuilder()
                                        .withName("SOURCE_URL")
                                        .withValue(sourceUrl)
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("SOURCE_SHA256")
                                        .withValue(sourceChecksum)
                                        .build())
                        .withVolumeMounts(workMount)
                        .withResources(resources("100m", "128Mi", "1", "256Mi", "3Gi"))
                        .build();

        final var importBoundaries =
                new ContainerBuilder()
                        .withName("import-boundaries")
                        .withImage(OSM2PGSQL_IMAGE)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(
                                "exec osm2pgsql --create --slim --drop --output=flex "
                                        + "--style=/config/boundaries.lua --schema=maps_osm_stage "
                                        + "--middle-schema=maps_osm_stage --database=\"$DATABASE_URL\" "
                                        + "/work/source.osm.pbf")
                        .withEnv(databaseUrl)
                        .withVolumeMounts(workMount, configMount)
                        .withResources(resources("500m", "1Gi", "4", "6Gi", "8Gi"))
                        .build();

        final var promote =
                new ContainerBuilder()
                        .withName("promote-boundaries")
                        .withImage(PROMOTION_IMAGE)
                        .withCommand("/bin/sh", "-c")
                        .withArgs(
                                "exec psql \"$DATABASE_URL\" -v import_run_id=\"$IMPORT_RUN_ID\" "
                                        + "-v source_timestamp=\"$SOURCE_TIMESTAMP\" -f /config/promote-boundaries.sql")
                        .withEnv(
                                databaseUrl,
                                new EnvVarBuilder()
                                        .withName("IMPORT_RUN_ID")
                                        .withValue(Long.toString(runId))
                                        .build(),
                                new EnvVarBuilder()
                                        .withName("SOURCE_TIMESTAMP")
                                        .withValue(sourceTimestamp.toString())
                                        .build())
                        .withVolumeMounts(configMount)
                        .withResources(resources("100m", "128Mi", "500m", "256Mi", "256Mi"))
                        .build();

        final Map<String, String> annotations =
                Map.of("std-dive-logger/import-run-id", Long.toString(runId));
        return new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withLabels(LABELS)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(1)
                .withActiveDeadlineSeconds(7200L)
                .withTtlSecondsAfterFinished(86400)
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(LABELS)
                .withAnnotations(annotations)
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withServiceAccountName("std-dive-logger-osm-importer")
                .withAutomountServiceAccountToken(false)
                .withImagePullSecrets(new LocalObjectReferenceBuilder().withName("regcred").build())
                .withInitContainers(download, importBoundaries)
                .withContainers(promote)
                .withVolumes(
                        new VolumeBuilder()
                                .withName("work")
                                .withEmptyDir(
                                        new EmptyDirVolumeSourceBuilder()
                                                .withSizeLimit(new Quantity("8Gi"))
                                                .build())
                                .build(),
                        new VolumeBuilder()
                                .withName("config")
                                .withConfigMap(
                                        new ConfigMapVolumeSourceBuilder()
                                                .withName("std-dive-logger-osm-import")
                                                .build())
                                .build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private static io.fabric8.kubernetes.api.model.EnvVar secretEnv(
            final String name, final String secretName, final String key) {
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

    private static io.fabric8.kubernetes.api.model.ResourceRequirements resources(
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

package ch.sthomas.stddivelogger.analytics.maps;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Calculates the durable state of everything that controls an OSM boundary import. */
@Component
@ConditionalOnProperty(
        name = {"maps.enabled", "maps.import.kubernetes.enabled"},
        havingValue = "true")
public class MapsImportStateCalculator {
    private static final List<String> CONFIG_FILES =
            List.of("boundaries.lua", "promote-boundaries.sql");

    private final MapsImportProperties properties;

    public MapsImportStateCalculator(final MapsImportProperties properties) {
        this.properties = properties;
    }

    public String calculate() {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "source-url", properties.getSourceUrl());
            update(digest, "source-checksum", properties.getSourceChecksum());
            update(digest, "database-secret", properties.getDatabaseSecretName());
            update(digest, "osm2pgsql-image", MapsImportJobFactory.OSM2PGSQL_IMAGE);
            update(digest, "download-image", MapsImportJobFactory.DOWNLOAD_IMAGE);
            update(digest, "promotion-image", MapsImportJobFactory.PROMOTION_IMAGE);
            final Path configDirectory = Path.of(properties.getConfigPath());
            for (final String fileName : CONFIG_FILES) {
                digest.update(fileName.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(configDirectory.resolve(fileName)));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read maps import configuration", exception);
        }
    }

    private static void update(final MessageDigest digest, final String name, final String value) {
        digest.update(name.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}

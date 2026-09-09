package ch.sthomas.stddivelogger.analytics.maps;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.data.service.MapsImportKind;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class MapsImportStateCalculatorTest {

    @TempDir Path configDirectory;

    @Test
    void stateIsStableUntilImportInputsChange() throws IOException {
        Files.writeString(configDirectory.resolve("boundaries.lua"), "first-style");
        final var properties = properties();
        final var calculator = new MapsImportStateCalculator(properties);

        final String initial = calculator.calculate(MapsImportKind.OSM);

        assertThat(calculator.calculate(MapsImportKind.OSM)).isEqualTo(initial);
        Files.writeString(configDirectory.resolve("boundaries.lua"), "second-style");
        assertThat(calculator.calculate(MapsImportKind.OSM)).isNotEqualTo(initial);
    }

    @Test
    void sourceConfigurationContributesToState() throws IOException {
        Files.writeString(configDirectory.resolve("boundaries.lua"), "style");
        final var properties = properties();
        final var calculator = new MapsImportStateCalculator(properties);
        final String initial = calculator.calculate(MapsImportKind.OSM);

        properties.setSourceUrl("https://example.test/another.osm.pbf");

        assertThat(calculator.calculate(MapsImportKind.OSM)).isNotEqualTo(initial);
    }

    @Test
    void everySourceHasItsOwnStateAndCgazTracksItsDownloadUrls() throws IOException {
        Files.writeString(configDirectory.resolve("boundaries.lua"), "style");
        final var properties = properties();
        final var calculator = new MapsImportStateCalculator(properties);
        final String initial = calculator.calculate(MapsImportKind.CGAZ);

        assertThat(initial).isNotEqualTo(calculator.calculate(MapsImportKind.OSM));

        properties.setCgazAdm1Url("https://example.test/ADM1.gpkg");

        assertThat(calculator.calculate(MapsImportKind.CGAZ)).isNotEqualTo(initial);
    }

    private MapsImportProperties properties() {
        final var properties = new MapsImportProperties();
        properties.setConfigPath(configDirectory.toString());
        return properties;
    }
}

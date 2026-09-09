package ch.sthomas.stddivelogger.analytics.maps;

import static org.assertj.core.api.Assertions.assertThat;

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
        Files.writeString(configDirectory.resolve("promote-boundaries.sql"), "first-promotion");
        final var properties = properties();
        final var calculator = new MapsImportStateCalculator(properties);

        final String initial = calculator.calculate();

        assertThat(calculator.calculate()).isEqualTo(initial);
        Files.writeString(configDirectory.resolve("boundaries.lua"), "second-style");
        assertThat(calculator.calculate()).isNotEqualTo(initial);
    }

    @Test
    void sourceConfigurationContributesToState() throws IOException {
        Files.writeString(configDirectory.resolve("boundaries.lua"), "style");
        Files.writeString(configDirectory.resolve("promote-boundaries.sql"), "promotion");
        final var properties = properties();
        final var calculator = new MapsImportStateCalculator(properties);
        final String initial = calculator.calculate();

        properties.setSourceUrl("https://example.test/another.osm.pbf");

        assertThat(calculator.calculate()).isNotEqualTo(initial);
    }

    private MapsImportProperties properties() {
        final var properties = new MapsImportProperties();
        properties.setConfigPath(configDirectory.toString());
        return properties;
    }
}

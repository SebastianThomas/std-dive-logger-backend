package ch.sthomas.stddivelogger.model.dive.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

class DecoSettingsTest {

    @Test
    void namesTheAlgorithmsTheWaySourcesSpellThem() {
        assertThat(DecoSettings.normalize("zhl16c")).isEqualTo("Bühlmann ZHL-16C");
        assertThat(DecoSettings.normalize("ZHL_16C")).isEqualTo("Bühlmann ZHL-16C");
        assertThat(DecoSettings.normalize("Buhlmann")).isEqualTo("Bühlmann ZHL-16C");
        assertThat(DecoSettings.normalize("VPM-B")).isEqualTo("VPM-B");
        assertThat(DecoSettings.normalize("dciem")).isEqualTo("DCIEM");
        assertThat(DecoSettings.normalize("Fused RGBM 2")).isEqualTo("Fused RGBM 2");
        assertThat(DecoSettings.normalize(" ")).isNull();
    }

    @Test
    void mergingKeepsWhatEitherSourceKnowsInEitherOrder() {
        // Like a UDDF (algorithm + GF only) and the native XML of the same dive (more fields).
        final var uddf =
                new DecoSettings(
                        "Bühlmann ZHL-16C",
                        "Shearwater",
                        50,
                        85,
                        null,
                        978.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("generator", "Shearwater Cloud"));
        final var xml =
                new DecoSettings(
                        "Bühlmann ZHL-16C",
                        "Shearwater",
                        50,
                        85,
                        null,
                        978.0,
                        null,
                        0.0,
                        6.0,
                        null,
                        null,
                        null,
                        null,
                        "66",
                        Map.of("decoModel", "0"));

        final var merged = Objects.requireNonNull(DecoSettings.merge(uddf, xml));

        assertThat(merged).isEqualTo(DecoSettings.merge(xml, uddf));
        assertThat(merged.endCns()).isEqualTo(6.0);
        assertThat(merged.firmware()).isEqualTo("66");
        assertThat(merged.details())
                .containsEntry("generator", "Shearwater Cloud")
                .containsEntry("decoModel", "0");
    }
}

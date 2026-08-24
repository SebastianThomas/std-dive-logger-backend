package ch.sthomas.stddivelogger.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

class LocationTimezoneResolverTest {

    private final LocationTimezoneResolver resolver = new LocationTimezoneResolver();

    @Test
    void resolvesARealCoastalDiveDestinationToItsKnownZone() {
        // Male, Maldives - a fixed UTC+5, no-DST zone, chosen so this assertion never needs
        // updating for the calendar date the test happens to run on.
        assertThat(resolver.resolve(4.1755, 73.5093)).contains(ZoneId.of("Indian/Maldives"));
    }

    @Test
    void resolvesASecondRealLocationToADifferentZone() {
        // Zurich, Switzerland - a real Shearwater-owning-diver location this project's own
        // fixtures/docs already reference (see AGENTS.md, SpringDocConfig's OpenAPI example).
        assertThat(resolver.resolve(47.3769, 8.5417)).contains(ZoneId.of("Europe/Zurich"));
    }

    @Test
    void resolvesTheOpenOceanToItsNauticalOffsetZone() {
        // Deep South Pacific, nowhere near any coastline - the underlying data covers the whole
        // globe via nautical Etc/GMT offset zones rather than leaving open water unresolved, so
        // ImportService.correctForUnknownTimezone's "no zone found" fallback is purely defensive
        // (kept for a genuinely out-of-range input, not a real gap in practice).
        assertThat(resolver.resolve(-40.0, -140.0)).contains(ZoneId.of("Etc/GMT+9"));
    }
}

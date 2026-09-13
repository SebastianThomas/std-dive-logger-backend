package ch.sthomas.stddivelogger.model.dive.gear;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class CylinderUsageWindowsTest {

    private static final Instant DIVE_START = Instant.parse("2026-09-13T08:30:00Z");
    private static final Gas AIR = new Gas(0.21, 0.0);
    private static final Gas EAN50 = new Gas(0.5, 0.0);

    // Air from the start, switch to EAN50 20 minutes in.
    private static final List<DiveMeasurement> PROFILE =
            List.of(sample(0, AIR), sample(600, AIR), sample(1200, EAN50), sample(1800, EAN50));

    private static DiveMeasurement sample(final long seconds, final Gas gas) {
        return new DiveMeasurement(
                DIVE_START.plusSeconds(seconds),
                null,
                10,
                null,
                List.of(),
                gas,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static boolean startsAtSwitch(final Gas gas, final CylinderUsageWindow window) {
        return CylinderUsageWindows.startsAtGasSwitch(gas, window, DIVE_START, PROFILE);
    }

    @Test
    void aWindowStartingAtTheSwitchOntoTheCylindersMixBelongsToTheProfile() {
        assertThat(startsAtSwitch(EAN50, new CylinderUsageWindow(Duration.ofSeconds(1230), null)))
                .isTrue();
    }

    @Test
    void theProfilesStartingMixCountsAsASwitchAtItsStart() {
        assertThat(
                        startsAtSwitch(
                                AIR,
                                new CylinderUsageWindow(Duration.ZERO, Duration.ofMinutes(20))))
                .isTrue();
    }

    @Test
    void anotherMixOrAWindowFarFromTheSwitchDoesNotBelongToIt() {
        assertThat(
                        startsAtSwitch(
                                new Gas(0.32, 0.0),
                                new CylinderUsageWindow(Duration.ofSeconds(1200), null)))
                .isFalse();
        // Right mix, but ten minutes after the switch onto it.
        assertThat(startsAtSwitch(EAN50, new CylinderUsageWindow(Duration.ofSeconds(1800), null)))
                .isFalse();
    }

    @Test
    void anOpenStartBelongsToNoSingleProfile() {
        assertThat(startsAtSwitch(AIR, new CylinderUsageWindow(null, Duration.ofMinutes(20))))
                .isFalse();
    }
}

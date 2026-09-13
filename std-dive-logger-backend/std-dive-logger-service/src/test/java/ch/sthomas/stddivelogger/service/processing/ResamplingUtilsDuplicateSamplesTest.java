package ch.sthomas.stddivelogger.service.processing;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Profiles refined before the refine merged instead of appending hold every sample two or three
 * times. Resampling (and so auto-alignment) must cope with repeated timestamps rather than divide
 * by their zero-length gaps.
 */
class ResamplingUtilsDuplicateSamplesTest {

    private static final Instant START = Instant.parse("2026-09-13T08:30:52Z");

    private static DiveMeasurementWithId sample(final long id, final long seconds) {
        // Down to 20 m in two minutes, a hold, back up - a shape alignment can lock onto.
        final double depth =
                seconds < 120
                        ? seconds / 6.0
                        : seconds < 600 ? 20 : Math.max(0, 20 - (seconds - 600) / 6.0);
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        START.plusSeconds(seconds),
                        null,
                        depth,
                        null,
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                id);
    }

    private static List<DiveMeasurementWithId> samples(final long step, final int copies) {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        long id = 0;
        for (long t = 0; t <= 720; t += step) {
            for (var copy = 0; copy < copies; copy++) {
                measurements.add(sample(id++, t));
            }
        }
        return measurements;
    }

    private static DiveProfile profile(
            final long id, final List<DiveMeasurementWithId> measurements) {
        return new DiveProfile(
                id,
                new DiveComputer(
                        id, new DiveComputerManufacturer(1L, "Test"), "S" + id, "C" + id, null),
                measurements.getFirst().measurement().time(),
                measurements.getLast().measurement().time(),
                measurements,
                null);
    }

    @Test
    void everySampleStoredTwiceStillResamplesToFiniteDepths() {
        final var measurements = samples(5, 2);

        final var info = ResamplingUtils.getResamplingInfo(measurements);
        final var resampled = ResamplingUtils.resampleMeasurements(measurements, info);

        assertThat(info.sampleRate()).isPositive();
        assertThat(resampled).isNotEmpty();
        assertThat(resampled).allSatisfy(r -> assertThat(r.depth()).isFinite());
    }

    @Test
    void autoAlignmentWorksAgainstAProfileHoldingEverySampleThreeTimes() {
        final var reference = profile(1, samples(10, 1));
        final var triplicated = profile(2, samples(5, 3));

        final var alignedStart =
                ProfileAlignService.alignProfilesAuto(
                        reference, triplicated, AlignType.AUTO_MIN_AVG_DISTANCE);

        assertThat(alignedStart).isEqualTo(START);
    }
}

package ch.sthomas.stddivelogger.service.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.assertj.core.data.TemporalUnitOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongToDoubleFunction;

class ProfileAlignServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-10T09:00:00Z");
    // Exact for the average distance; the others land within a few seconds, from
    // interpolating a 5-10 s profile against a 1 s one.
    private static final TemporalUnitOffset TOLERANCE = within(5, ChronoUnit.SECONDS);

    /** A 50 min dive: 3 min descent to 28 m, a step to 20 m, a 6 m stop, surface. */
    private static double dive(final long s) {
        if (s < 0 || s > 3000) {
            return 0;
        }
        if (s < 180) {
            return s * 28.0 / 180;
        }
        if (s < 1200) {
            return 28 + Math.sin(s / 60.0);
        }
        if (s < 2100) {
            return 20 + Math.cos(s / 90.0);
        }
        if (s < 2400) {
            return 20 - (s - 2100) * 14.0 / 300;
        }
        if (s < 2880) {
            return 6;
        }
        return 6 - (s - 2880) * 6.0 / 120;
    }

    private static DiveProfile profile(
            final long id,
            final Instant start,
            final long seconds,
            final long step,
            final LongToDoubleFunction depthAt) {
        final var measurements = new ArrayList<DiveMeasurementWithId>();
        for (long t = 0; t <= seconds; t += step) {
            measurements.add(
                    sample(id * 100_000 + t, start.plusSeconds(t), depthAt.applyAsDouble(t)));
        }
        return profile(id, measurements);
    }

    private static DiveProfile profile(final long id, final List<DiveMeasurementWithId> samples) {
        return new DiveProfile(
                id,
                new DiveComputer(
                        id, new DiveComputerManufacturer(1L, "Test"), "S" + id, "C" + id, null),
                samples.getFirst().measurement().time(),
                samples.getLast().measurement().time(),
                samples,
                null);
    }

    private static DiveMeasurementWithId sample(final long id, final Instant time, final double d) {
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        time, null, d, null, List.of(), null, null, null, null, null, null, null,
                        null),
                id);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void shortProfileFindsItsPlaceDeepInsideALongOne(final AlignType type) {
        // Long recording: 40 min at the surface before the dive, 30 min after it.
        final var longOne = profile(1, T0, 40 * 60 + 3000 + 30 * 60, 1, s -> dive(s - 2400));
        // The same dive on a 5 s computer, its clock 7 min off.
        final var shortOne =
                profile(
                        2,
                        T0.plus(Duration.ofMinutes(40 - 7)),
                        3000,
                        5,
                        ProfileAlignServiceTest::dive);

        assertThat(ProfileAlignService.alignProfilesAuto(longOne, shortOne, type))
                .isCloseTo(T0.plus(Duration.ofMinutes(40)), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void longTargetMovesOntoAShortReference(final AlignType type) {
        final var shortOne = profile(1, T0, 3000, 5, ProfileAlignServiceTest::dive);
        // Starts logging 25 min before the descent, and its clock is 3 min ahead.
        final var longOne =
                profile(
                        2,
                        T0.minus(Duration.ofMinutes(25)).plus(Duration.ofMinutes(3)),
                        25 * 60 + 3000 + 20 * 60,
                        1,
                        s -> dive(s - 1500));

        assertThat(ProfileAlignService.alignProfilesAuto(shortOne, longOne, type))
                .isCloseTo(T0.minus(Duration.ofMinutes(25)), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void shortProfilePicksTheMatchingDiveOfTwoInOneRecording(final AlignType type) {
        // First a shallow 12 m dive, a 25 min surface interval, then the real one.
        final var twoDives =
                profile(
                        1,
                        T0,
                        1800 + 1500 + 3000,
                        1,
                        s ->
                                s < 1800
                                        ? Math.min(12, Math.min(s, 1800 - s) / 10.0)
                                        : dive(s - 3300));
        final var second = profile(2, T0, 3000, 5, ProfileAlignServiceTest::dive);

        assertThat(ProfileAlignService.alignProfilesAuto(twoDives, second, type))
                .isCloseTo(T0.plusSeconds(3300), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void aProfileTrimmedAtDepthIsNotTakenToBeAtTheSurfaceBeyondItsEdges(final AlignType type) {
        final var whole = profile(1, T0, 3000, 1, ProfileAlignServiceTest::dive);
        // Only minutes 15-40 kept (at 20-28 m both ends), then moved 25 min off.
        final var trimmed =
                profile(2, T0.plus(Duration.ofMinutes(40)), 1500, 5, s -> dive(s + 900));

        assertThat(ProfileAlignService.alignProfilesAuto(whole, trimmed, type))
                .isCloseTo(T0.plus(Duration.ofMinutes(15)), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void aProfileHoldingEverySampleThreeTimesStillAligns(final AlignType type) {
        final var reference = profile(1, T0, 3000, 10, ProfileAlignServiceTest::dive);
        final var triplicated = new ArrayList<DiveMeasurementWithId>();
        for (long t = 0; t <= 3000; t += 5) {
            for (var copy = 0; copy < 3; copy++) {
                triplicated.add(sample(t * 3 + copy, T0.plusSeconds(t + 120), dive(t)));
            }
        }

        assertThat(ProfileAlignService.alignProfilesAuto(reference, profile(2, triplicated), type))
                .isCloseTo(T0, TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(value = AlignType.class, names = "AUTO_.*", mode = EnumSource.Mode.MATCH_ANY)
    void aProfileThatNeverLeavesTheSurfaceIsRejected(final AlignType type) {
        final var dive = profile(1, T0, 3000, 5, ProfileAlignServiceTest::dive);
        final var surface = profile(2, T0, 600, 5, s -> 0.4);

        assertThatThrownBy(() -> ProfileAlignService.alignProfilesAuto(dive, surface, type))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package ch.sthomas.stddivelogger.model.dive.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class ReimportSimilarityCheckTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static DiveMeasurement sample(final Instant time, final double depth) {
        return new DiveMeasurement(
                time, null, depth, null, List.of(), null, null, null, null, null, null, null, null);
    }

    /**
     * A steady descent to maxDepth by the midpoint, held, then a steady ascent - deterministic
     * depth at every fraction of the dive, so tests can assert exact expected tolerances.
     */
    private static List<DiveMeasurement> triangularProfile(
            final Instant start, final Duration duration, final double maxDepth) {
        final var measurements = new ArrayList<DiveMeasurement>();
        final var totalSeconds = duration.toSeconds();
        for (long t = 0; t <= totalSeconds; t += 10) {
            final var fraction = (double) t / totalSeconds;
            final var depth =
                    fraction <= 0.5
                            ? maxDepth * (fraction / 0.5)
                            : maxDepth * (1 - (fraction - 0.5) / 0.5);
            measurements.add(sample(start.plusSeconds(t), depth));
        }
        return measurements;
    }

    @Test
    void identicalProfileIsAMatch() {
        final var profile = triangularProfile(START, Duration.ofMinutes(40), 30.0);

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        profile,
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        profile);

        assertThat(result).isEmpty();
    }

    @Test
    void smallCrossFormatDifferencesAreAMatch() {
        // Mirrors the real Suunto FIT-vs-JSON gaps already characterized: start within ~1s, max
        // depth within ~0.5m, duration matching almost exactly.
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var newStart = START.plusSeconds(1);
        final var newProfile = triangularProfile(newStart, Duration.ofMinutes(40), 30.3);

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        newStart,
                        newStart.plus(Duration.ofMinutes(40)),
                        newProfile);

        assertThat(result).isEmpty();
    }

    @Test
    void differentStartTimeIsRejected() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var otherDay = START.plus(Duration.ofDays(1));
        final var newProfile = triangularProfile(otherDay, Duration.ofMinutes(40), 30.0);

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        otherDay,
                        otherDay.plus(Duration.ofMinutes(40)),
                        newProfile);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("start time");
    }

    @Test
    void muchShorterDurationIsRejected() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var newProfile = triangularProfile(START, Duration.ofMinutes(10), 30.0);

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        START,
                        START.plus(Duration.ofMinutes(10)),
                        newProfile);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("duration");
    }

    @Test
    void muchShallowerMaxDepthIsRejected() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var newProfile = triangularProfile(START, Duration.ofMinutes(40), 10.0);

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        newProfile);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("max depth");
    }

    @Test
    void sameMaxDepthAndDurationButDifferentShapeIsRejected() {
        // Same duration and max depth, but the new profile is a flat 30m dive the whole time
        // (e.g. a wreck penetration) instead of a bounce dive - a real "different dive" case that
        // start/duration/maxDepth alone wouldn't catch.
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var flat = new ArrayList<DiveMeasurement>();
        for (long t = 0; t <= 2400; t += 10) {
            flat.add(sample(START.plusSeconds(t), 30.0));
        }

        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        flat);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("shape");
    }

    @Test
    void wholeHourClockOffsetIsDetectedWhenEverythingElseMatches() {
        // Same dive, existing profile 2h "behind" the reimport - the UTC-vs-local-zone case
        // (e.g. a Shearwater XML corrected to a real site, reimported with the naive-UTC UDDF).
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var newStart = START.plus(Duration.ofHours(2)).plusSeconds(1);
        final var newProfile = triangularProfile(newStart, Duration.ofMinutes(40), 30.2);

        final var offset =
                ReimportSimilarityCheck.wholeHourClockOffset(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        newStart,
                        newStart.plus(Duration.ofMinutes(40)),
                        newProfile);

        assertThat(offset).contains(Duration.ofHours(2));
    }

    @Test
    void wholeHourClockOffsetIsIgnoredWhenTheProfilesAlsoDisagreeOnShape() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var newStart = START.plus(Duration.ofHours(2));
        final var newProfile = triangularProfile(newStart, Duration.ofMinutes(12), 30.0);

        final var offset =
                ReimportSimilarityCheck.wholeHourClockOffset(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        existing,
                        newStart,
                        newStart.plus(Duration.ofMinutes(12)),
                        newProfile);

        assertThat(offset).isEmpty();
    }

    @Test
    void requirePlausibleReimportReturnsTheOffsetForAZoneArtefactAndThrowsForADifferentDive() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);

        final var shifted = START.plus(Duration.ofHours(3));
        assertThat(
                        ReimportSimilarityCheck.requirePlausibleReimport(
                                START,
                                START.plus(Duration.ofMinutes(40)),
                                existing,
                                shifted,
                                shifted.plus(Duration.ofMinutes(40)),
                                triangularProfile(shifted, Duration.ofMinutes(40), 30.0)))
                .contains(Duration.ofHours(3));

        final var otherDive = START.plus(Duration.ofHours(2));
        assertThatThrownBy(
                        () ->
                                ReimportSimilarityCheck.requirePlausibleReimport(
                                        START,
                                        START.plus(Duration.ofMinutes(40)),
                                        existing,
                                        otherDive,
                                        otherDive.plus(Duration.ofMinutes(40)),
                                        triangularProfile(otherDive, Duration.ofMinutes(40), 10.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doesn't look like the same dive")
                .hasMessageContaining("merge profiles");
    }

    @Test
    void aStartGapLargerThanAnyRealTimezoneStaysAHardMismatch() {
        final var existing = triangularProfile(START, Duration.ofMinutes(40), 30.0);
        final var farOff = START.plus(Duration.ofHours(20));

        assertThat(
                        ReimportSimilarityCheck.wholeHourClockOffset(
                                START,
                                START.plus(Duration.ofMinutes(40)),
                                existing,
                                farOff,
                                farOff.plus(Duration.ofMinutes(40)),
                                triangularProfile(farOff, Duration.ofMinutes(40), 30.0)))
                .isEmpty();
        assertThatThrownBy(
                        () ->
                                ReimportSimilarityCheck.requirePlausibleReimport(
                                        START,
                                        START.plus(Duration.ofMinutes(40)),
                                        existing,
                                        farOff,
                                        farOff.plus(Duration.ofMinutes(40)),
                                        triangularProfile(farOff, Duration.ofMinutes(40), 30.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyMeasurementListsDoNotCrashAndAreNotRejectedOnDepthAlone() {
        // Guards the depth/curve checks against empty lists (e.g. an events-only degenerate
        // parse) - start/duration checks still apply and can still reject on their own.
        final var result =
                ReimportSimilarityCheck.checkSameDive(
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        List.of(),
                        START,
                        START.plus(Duration.ofMinutes(40)),
                        List.of());

        assertThat(result).isEmpty();
    }
}

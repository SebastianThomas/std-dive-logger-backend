package ch.sthomas.stddivelogger.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.AutoDetectRule;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Covers {@code matchesAutoDetect(DECO)} (which delegates to the private {@code hasDeco()}) - in
 * particular the fix from summing every measurement's {@code DecoStop.seconds()} to taking the
 * peak. Summing is wrong because that field is a remaining-time reading (FIT's next_stop_time,
 * Suunto's TimeToSurface), not a per-sample duration - many samples through one real stop would
 * otherwise inflate the "total" far past the real elapsed time. The 5-minute threshold is checked
 * against two real dives (see SuuntoJsonReaderServiceTest): dive-1-deco peaks at 532s (~8.9min, a
 * real deco obligation) and dive-2-nodeco peaks at only 188s (~3.1min, a brief NDL scratch).
 */
class DiveEntityTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private static DiveMeasurementEntity measurementWithDeco(final long decoSeconds) {
        return new DiveMeasurementEntity(
                new DiveMeasurement(
                        START,
                        null,
                        20.0,
                        null,
                        List.of(new DecoStop("mandatory", 6, decoSeconds)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null);
    }

    private static DiveMeasurementEntity measurementWithNoDeco() {
        return new DiveMeasurementEntity(
                new DiveMeasurement(
                        START, null, 20.0, null, List.of(), null, null, null, null, null, null,
                        null, null),
                null);
    }

    private static DiveProfileEntity profileOf(final DiveMeasurementEntity... measurements) {
        return new DiveProfileEntity(
                new DiveComputerEntity(), START, START.plusSeconds(60), List.of(measurements));
    }

    private static DiveEntity diveWithProfiles(final List<DiveProfileEntity> profiles) {
        final var dive = new DiveEntity();
        ReflectionTestUtils.setField(dive, "profiles", profiles);
        return dive;
    }

    @Test
    void noProfilesNeverMatchesDeco() {
        final var dive = diveWithProfiles(List.of());
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isFalse();
    }

    @Test
    void nullRuleNeverMatches() {
        final var dive = diveWithProfiles(List.of(profileOf(measurementWithNoDeco())));
        assertThat(dive.matchesAutoDetect(null)).isFalse();
    }

    @Test
    void belowFiveMinutePeakDoesNotMatch() {
        final var dive = diveWithProfiles(List.of(profileOf(measurementWithDeco(299))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isFalse();
    }

    @Test
    void fiveMinutePeakExactlyMatches() {
        final var dive = diveWithProfiles(List.of(profileOf(measurementWithDeco(300))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isTrue();
    }

    @Test
    void aRealWorldDecoDivePeakMatches() {
        final var dive = diveWithProfiles(List.of(profileOf(measurementWithDeco(532))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isTrue();
    }

    @Test
    void aRealWorldNdlScratchBelowThresholdDoesNotMatch() {
        final var dive = diveWithProfiles(List.of(profileOf(measurementWithDeco(188))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isFalse();
    }

    @Test
    void manyShortSamplesDuringOneStopDoNotSumPastTheThreshold() {
        // 10 samples of a genuine 60s remaining-time reading must not manufacture a fake 600s
        // (10min) total the way summing would.
        final var measurements =
                IntStream.range(0, 10)
                        .mapToObj(i -> measurementWithDeco(60))
                        .toArray(DiveMeasurementEntity[]::new);
        final var dive = diveWithProfiles(List.of(profileOf(measurements)));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isFalse();
    }

    @Test
    void thePeakMeasurementIsUsedRegardlessOfPosition() {
        final var dive =
                diveWithProfiles(
                        List.of(
                                profileOf(
                                        measurementWithDeco(100),
                                        measurementWithDeco(400),
                                        measurementWithDeco(50))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isTrue();
    }

    @Test
    void maxIsTakenAcrossAllProfilesNotJustOne() {
        final var dive =
                diveWithProfiles(
                        List.of(
                                profileOf(measurementWithDeco(100)),
                                profileOf(measurementWithDeco(400))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isTrue();
    }

    @Test
    void emptyDecoStopsListsAreIgnoredRatherThanCountingAsZero() {
        final var dive =
                diveWithProfiles(
                        List.of(profileOf(measurementWithNoDeco(), measurementWithDeco(400))));
        assertThat(dive.matchesAutoDetect(AutoDetectRule.DECO)).isTrue();
    }
}

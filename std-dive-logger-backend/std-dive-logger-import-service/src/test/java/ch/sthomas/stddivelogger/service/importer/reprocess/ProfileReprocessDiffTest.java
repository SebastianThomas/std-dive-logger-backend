package ch.sthomas.stddivelogger.service.importer.reprocess;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.profile.DecoSettings;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class ProfileReprocessDiffTest {
    private static final Instant T0 = Instant.parse("2026-09-13T08:30:00Z");

    private static DiveMeasurement sample(
            final int second, final double depth, final @Nullable Duration tts) {
        return new DiveMeasurement(
                T0.plusSeconds(second),
                null,
                depth,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                tts);
    }

    private static DiveMeasurement withNdl(final DiveMeasurement m, final Duration ndl) {
        return new DiveMeasurement(
                m.time(),
                m.temperature(),
                m.depth(),
                ndl,
                m.deco(),
                m.gas(),
                m.po2(),
                m.rmvLiters(),
                m.n2(),
                m.o2Tox(),
                m.cns(),
                m.mode(),
                m.timeToSurface());
    }

    private static DecoSettings deco(final @Nullable Integer gfHigh) {
        return new DecoSettings(
                "Bühlmann ZHL-16C",
                null,
                30,
                gfHigh,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of());
    }

    private static ProfileReprocessDiff.Result compare(
            final List<DiveMeasurement> current, final List<DiveMeasurement> proposed) {
        return ProfileReprocessDiff.compare(current, null, proposed, null, Duration.ZERO);
    }

    @Test
    void anUnchangedFileIsIdenticalEvenThoughTheDatabaseKeepsNdlInWholeMinutes() {
        final var stored = List.of(withNdl(sample(0, 3.0, null), Duration.ofMinutes(5)));
        final var reparsed = List.of(withNdl(sample(0, 3.0, null), Duration.ofSeconds(340)));

        final var result = compare(stored, reparsed);

        assertThat(result.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.IDENTICAL);
        assertThat(result.summary()).isEqualTo("No changes");
    }

    @Test
    void valuesAndSamplesTheStoredProfileLacksAreOnlyAdditions() {
        final var stored = List.of(sample(0, 1.0, null), sample(10, 5.0, null));
        final var reparsed =
                List.of(
                        sample(0, 1.0, Duration.ofSeconds(60)),
                        sample(10, 5.0, Duration.ofSeconds(90)),
                        sample(20, 6.0, Duration.ofSeconds(120)));

        final var result = compare(stored, reparsed);

        assertThat(result.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.ADDITIONS_ONLY);
        assertThat(result.summary()).isEqualTo("1 sample added; TTS added to 2 samples");
    }

    @Test
    void aValueTheFileNowReadsDifferentlyIsAChange() {
        final var stored = List.of(sample(0, 1.0, Duration.ofSeconds(60)));
        final var reparsed = List.of(sample(0, 1.0, Duration.ofSeconds(90)));

        final var result = compare(stored, reparsed);

        assertThat(result.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.CHANGED);
        assertThat(result.summary()).isEqualTo("TTS changes on 1 sample");
    }

    @Test
    void aSampleOrValueTheFileNoLongerHasIsAChangeNotSilentlyDropped() {
        final var stored = List.of(sample(0, 1.0, Duration.ofSeconds(60)), sample(10, 5.0, null));
        final var reparsed = List.of(sample(0, 1.0, null));

        final var result = compare(stored, reparsed);

        assertThat(result.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.CHANGED);
        assertThat(result.summary()).isEqualTo("TTS disappears from 1 sample; 1 sample removed");
    }

    @Test
    void aClockShiftIsOneChangeWhileTheSamplesAreComparedWithoutIt() {
        final var stored = List.of(sample(0, 1.0, null), sample(10, 5.0, null));
        final var shift = Duration.ofHours(-2);
        final var reparsed = stored.stream().map(m -> m.shifted(shift)).toList();

        final var result = ProfileReprocessDiff.compare(stored, null, reparsed, null, shift);

        assertThat(result.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.CHANGED);
        assertThat(result.summary()).isEqualTo("Clock moves 2 h earlier");
    }

    @Test
    void decoSettingsGainingAValueIsAnAdditionAndChangingOneIsAChange() {
        final var samples = List.of(sample(0, 1.0, null));

        final var gained =
                ProfileReprocessDiff.compare(samples, deco(null), samples, deco(70), Duration.ZERO);
        final var changed =
                ProfileReprocessDiff.compare(samples, deco(70), samples, deco(85), Duration.ZERO);

        assertThat(gained.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.ADDITIONS_ONLY);
        assertThat(gained.summary()).isEqualTo("Deco settings gain GF high");
        assertThat(changed.outcome()).isEqualTo(ProfileReprocessDiff.Outcome.CHANGED);
        assertThat(changed.summary()).isEqualTo("Deco settings: GF high 70 → 85");
    }

    @Test
    void shiftsAreDescribedInHoursMinutesAndSeconds() {
        assertThat(ProfileReprocessDiff.describeShift(Duration.ofMinutes(-90)))
                .isEqualTo("1 h 30 min earlier");
        assertThat(ProfileReprocessDiff.describeShift(Duration.ofSeconds(45)))
                .isEqualTo("45 s later");
    }
}

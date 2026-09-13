package ch.sthomas.stddivelogger.model.dive.profile;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class ProfileMeasurementMergeTest {

    private static final Instant START = Instant.parse("2026-09-13T08:30:52Z");

    private static DiveMeasurement sample(
            final long seconds,
            final double depth,
            final @Nullable Double cns,
            final @Nullable Duration tts) {
        return new DiveMeasurement(
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
                cns,
                null,
                tts);
    }

    // Like a UDDF export: CNS on every sample, no TTS, ends at 20 s.
    private static List<DiveMeasurement> uddf() {
        final var samples = new ArrayList<DiveMeasurement>();
        for (long t = 0; t <= 20; t += 5) {
            samples.add(sample(t, 10, 5.0, null));
        }
        return samples;
    }

    // Like the native XML of the same dive: TTS on every sample, no CNS, runs on until 30 s.
    private static List<DiveMeasurement> xml() {
        final var samples = new ArrayList<DiveMeasurement>();
        for (long t = 0; t <= 30; t += 5) {
            samples.add(sample(t, 10, null, Duration.ofMinutes(3)));
        }
        return samples;
    }

    @Test
    void sharedSamplesCarryTheFieldsOfBothRecordings() {
        final var merged = ProfileMeasurementMerge.merge(uddf(), xml());

        final var shared = merged.getFirst();
        assertThat(shared.cns()).isEqualTo(5.0);
        assertThat(shared.timeToSurface()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void samplesOnlyOneRecordingHasAreKept() {
        final var merged = ProfileMeasurementMerge.merge(uddf(), xml());

        assertThat(merged).extracting(DiveMeasurement::time).doesNotHaveDuplicates().hasSize(7);
        assertThat(merged.getLast().time()).isEqualTo(START.plusSeconds(30));
    }

    @Test
    void theResultDoesNotDependOnWhichRecordingCameFirst() {
        final var conflicting =
                xml().stream()
                        .map(
                                m ->
                                        sample(
                                                m.time().getEpochSecond() - START.getEpochSecond(),
                                                11,
                                                null,
                                                m.timeToSurface()))
                        .toList();

        assertThat(ProfileMeasurementMerge.merge(uddf(), conflicting))
                .isEqualTo(ProfileMeasurementMerge.merge(conflicting, uddf()));
        assertThat(ProfileMeasurementMerge.merge(uddf(), xml()))
                .isEqualTo(ProfileMeasurementMerge.merge(xml(), uddf()));
    }

    @Test
    void samplesWithinASecondAreTheSameSample() {
        final var shifted = xml().stream().map(m -> m.shifted(Duration.ofMillis(400))).toList();

        assertThat(ProfileMeasurementMerge.merge(uddf(), shifted)).hasSize(7);
    }
}

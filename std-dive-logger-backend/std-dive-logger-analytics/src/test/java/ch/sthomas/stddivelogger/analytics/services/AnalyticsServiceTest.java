package ch.sthomas.stddivelogger.analytics.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.data.service.AnalyticsDataService;
import ch.sthomas.stddivelogger.data.service.DiveDataService;
import ch.sthomas.stddivelogger.data.service.DiveSiteStatsDataService;
import ch.sthomas.stddivelogger.data.service.DiverActivityStatsDataService;
import ch.sthomas.stddivelogger.data.service.DiverReminderDataService;
import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegmentWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.service.PushService;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class AnalyticsServiceTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private final AnalyticsService service =
            new AnalyticsService(
                    mock(AnalyticsDataService.class),
                    mock(AnalyticsSegmentService.class),
                    mock(DiveDataService.class),
                    mock(DiverActivityStatsDataService.class),
                    mock(DiverReminderDataService.class),
                    mock(PushService.class),
                    mock(DiveSiteStatsDataService.class));

    private static DiveMeasurementWithId sample(final int offsetSeconds, final double depth) {
        return new DiveMeasurementWithId(
                new DiveMeasurement(
                        START.plusSeconds(offsetSeconds),
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
                offsetSeconds + 1L);
    }

    private static DiveProfileSegmentWithId segment(
            final List<DiveMeasurementWithId> measurements) {
        final var computer =
                new DiveComputer(
                        1, new DiveComputerManufacturer(1, "Test"), "SN", "Computer", null);
        final var profile =
                new DiveProfile(
                        1,
                        computer,
                        measurements.getFirst().measurement().time(),
                        measurements.getLast().measurement().time(),
                        measurements,
                        true);
        return new DiveProfileSegmentWithId(
                new DiveProfileSegment(profile, 0, DiveProfileSegmentType.HOLD_LEVEL, measurements),
                1);
    }

    @Test
    void skipsANonFiniteDepthMeasurementRatherThanPoisoningTheStats() {
        final var good0 = sample(0, 10);
        final var badNaN = sample(30, Double.NaN);
        final var good1 = sample(60, 20);

        final var result = service.createAnalytics(segment(List.of(good0, badNaN, good1)));

        assertTrue(Double.isFinite(result.stats().avgDepth()));
        assertTrue(Double.isFinite(result.stats().maxDepth()));
        assertTrue(Double.isFinite(result.stats().minDepth()));
        assertTrue(Double.isFinite(result.stats().deviationAvg()));
    }

    @Test
    void fallsBackToTheSingleFiniteMeasurementWhenOnlyOneRemainsAfterFiltering() {
        final var badNaN = sample(0, Double.NaN);
        final var good = sample(30, 15);

        final var result = service.createAnalytics(segment(List.of(badNaN, good)));

        assertEquals(15, result.stats().avgDepth());
        assertEquals(15, result.stats().maxDepth());
        assertEquals(15, result.stats().minDepth());
    }

    @Test
    void producesFiniteStatsWhenAllDepthsAreValid() {
        final var m0 = sample(0, 0);
        final var m1 = sample(30, 10);
        final var m2 = sample(60, 5);

        final var result = service.createAnalytics(segment(List.of(m0, m1, m2)));

        assertEquals(10, result.stats().maxDepth());
        assertEquals(0, result.stats().minDepth());
    }
}

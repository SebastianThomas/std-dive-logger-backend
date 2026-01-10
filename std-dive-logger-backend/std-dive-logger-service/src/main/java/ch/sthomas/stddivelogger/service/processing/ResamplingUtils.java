package ch.sthomas.stddivelogger.service.processing;

import ch.sthomas.stddivelogger.model.dive.profile.align.ResampledDiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.align.ResamplingInfo;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

public class ResamplingUtils {
    private static final Logger logger = LoggerFactory.getLogger(ResamplingUtils.class);

    public static ResamplingInfo getResamplingInfo(final List<DiveMeasurementWithId> measurements) {
        final var measurementRateRaw = measurementRate(measurements);
        final var baseTime = measurements.getFirst().measurement().time();
        final var measurementRateSubSecond = measurementRateSubSecond(measurementRateRaw);
        return new ResamplingInfo(measurementRateSubSecond, baseTime);
    }

    public static List<ResampledDiveMeasurement> resampleMeasurements(
            final List<DiveMeasurementWithId> measurements, final ResamplingInfo info) {
        var start = info.baseTime();
        final var firstOriginalMeasurementTime = measurements.getFirst().measurement().time();
        final var lastOriginalMeasurementTime = measurements.getLast().measurement().time();
        while (start.isAfter(firstOriginalMeasurementTime)) {
            start = start.minus(info.sampleRate());
        }
        while (start.isBefore(firstOriginalMeasurementTime)) {
            start = start.plus(info.sampleRate());
        }
        final var measurementIdx = new AtomicInteger(0);
        return Stream.iterate(
                        start,
                        // Duration.ofMillis(startMs),
                        d -> !d.isAfter(lastOriginalMeasurementTime),
                        d -> d.plus(info.sampleRate()))
                .map(d -> getResampledMeasurement(measurements, measurementIdx, d))
                .toList();
    }

    private static @NonNull ResampledDiveMeasurement getResampledMeasurement(
            final List<DiveMeasurementWithId> measurements,
            final AtomicInteger measurementIdx,
            final Instant d) {
        var current = measurementIdx.get();
        if (current < measurements.size() - 1
                && !measurements.get(current + 1).measurement().time().isAfter(d)) {
            current = measurementIdx.incrementAndGet();
        }
        final var nextIdx = current + 1;
        final var currentMeasurement = measurements.get(current);
        final var nextMeasurementOpt =
                nextIdx >= measurements.size()
                        ? Optional.<DiveMeasurementWithId>empty()
                        : Optional.of(measurements.get(current + 1));
        if (currentMeasurement.measurement().time().equals(d) || nextMeasurementOpt.isEmpty()) {
            return new ResampledDiveMeasurement(d, currentMeasurement.measurement().depth());
        }
        final var nextMeasurement = nextMeasurementOpt.get();
        if (nextMeasurement.measurement().time().equals(d)) {
            return new ResampledDiveMeasurement(d, nextMeasurement.measurement().depth());
        }
        final var diff =
                Duration.between(
                        currentMeasurement.measurement().time(),
                        nextMeasurement.measurement().time());
        final var nextMeasurementDiff = Duration.between(d, nextMeasurement.measurement().time());
        final var curFrac = (double) nextMeasurementDiff.toMillis() / diff.toMillis();
        if (nextMeasurementDiff.isNegative() || curFrac < 0 || curFrac > 1) {
            logger.info(
                    "Illegal State: Got {} Measurements, current idx: {}, current fraction: {}, d: {} and next measurement: {}",
                    measurements.size(),
                    measurementIdx.get(),
                    curFrac,
                    d,
                    nextMeasurement);
            throw new IllegalStateException();
        }
        final var depth =
                currentMeasurement.measurement().depth() * curFrac
                        + nextMeasurement.measurement().depth() * (1 - curFrac);
        return new ResampledDiveMeasurement(d, depth);
    }

    private static Duration measurementRate(final List<DiveMeasurementWithId> measurements) {
        return measurements.stream()
                .gather(Gatherers.windowSliding(2))
                .map(
                        l ->
                                Duration.between(
                                        l.getFirst().measurement().time(),
                                        l.getLast().measurement().time()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private static Duration measurementRateSubSecond(Duration measurementRateRaw) {
        final var seconds = measurementRateRaw.dividedBy(Duration.ofSeconds(1));
        if (Duration.ofSeconds(1).multipliedBy(seconds).equals(measurementRateRaw)) {
            return Duration.ofSeconds(1);
        }
        while (measurementRateRaw.minusSeconds(1).isPositive()) {
            measurementRateRaw = measurementRateRaw.dividedBy(2);
        }
        return measurementRateRaw;
    }
}

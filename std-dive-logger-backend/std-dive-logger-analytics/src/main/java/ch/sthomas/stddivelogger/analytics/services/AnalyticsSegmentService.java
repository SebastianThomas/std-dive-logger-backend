package ch.sthomas.stddivelogger.analytics.services;

import ch.sthomas.stddivelogger.model.analytics.DiveProfileSegmentType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSegment;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;

import com.nimbusds.jose.util.Pair;

import jakarta.annotation.Nullable;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Gatherer;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@Service
public class AnalyticsSegmentService {

    private static final int WINDOW_SIZE = 10;

    public Stream<DiveProfileSegment> createSegmentForProfile(final DiveProfile profile) {
        return Stream.of(
                new DiveProfileSegment(
                        profile, 0, DiveProfileSegmentType.UNKNOWN, profile.measurements()));
    }

    record TimeDepth(Instant time, double depth) {}

    static class Previous<T> {
        boolean hasPrevious;
        T previous;
        int startIdx;

        public Previous() {
            this.hasPrevious = false;
            this.startIdx = 0;
        }
    }

    public Stream<DiveProfileSegment> createSegments(final DiveProfile profile) {
        if (profile.measurements() == null) {
            throw new IllegalArgumentException(
                    "To create segments, profile.measurements must not be null");
        }
        final var len = profile.measurements().size();
        if (len <= WINDOW_SIZE) {
            return createSegmentForProfile(profile);
        }

        return profile.measurements().stream()
                // .gather(Gatherers.windowSliding(2))
                // .flatMap(AnalyticsSegmentService::getSecondTimeDepth)
                .gather(slidingWithTail(WINDOW_SIZE))
                .gather(
                        Gatherer.ofSequential(
                                () -> new Previous<DiveProfileSegmentType>(),
                                (prev, el, downstream) -> {
                                    final var cur =
                                            toWindowInfo(profile, prev.startIdx, prev.previous, el);
                                    prev.hasPrevious = true;
                                    prev.previous = cur.type();
                                    prev.startIdx++;
                                    return true;
                                }));
    }

    private DiveProfileSegment toWindowInfo(
            final DiveProfile profile,
            final int idx,
            @Nullable final DiveProfileSegmentType prevType,
            final List<DiveMeasurementWithId> window) {
        if (window.isEmpty()) {
            throw new IllegalArgumentException("Empty Windows cannot be processed.");
        }
        if (window.size() == 1) {
            if (window.getFirst().measurement().depth() == 0.0) {
                return new DiveProfileSegment(profile, idx, DiveProfileSegmentType.SURFACE, window);
            }
            return new DiveProfileSegment(profile, idx, prevType, window);
        }
        final var diffPerMinute =
                window.stream()
                        .skip(1)
                        .map(m -> Pair.of(window.getFirst(), m))
                        .map(
                                l ->
                                        (l.getRight().measurement().depth()
                                                        - l.getLeft().measurement().depth())
                                                / (Duration.between(
                                                                        l.getLeft()
                                                                                .measurement()
                                                                                .time(),
                                                                        l.getRight()
                                                                                .measurement()
                                                                                .time())
                                                                .toSeconds()
                                                        / 60.0))
                        .toList();

        final var type = getType(diffPerMinute, prevType);
        return new DiveProfileSegment(profile, idx, type, window);
    }

    private static DiveProfileSegmentType getType(
            final List<Double> diffPerMinute, final DiveProfileSegmentType prev) {
        if (Math.abs(diffPerMinute.getFirst()) > 3.0) {
            final var firstDiff = diffPerMinute.getFirst();
            return firstDiff < 0 ? DiveProfileSegmentType.DESCENT : DiveProfileSegmentType.ASCENT;
        }
        if (diffPerMinute.stream().limit(5).allMatch(d -> d < 0)) {
            final var maxDiff =
                    diffPerMinute.stream().mapToDouble(d -> d).limit(5).max().orElseThrow();
            return maxDiff < -3.0
                    ? DiveProfileSegmentType.DESCENT
                    : DiveProfileSegmentType.LIGHT_DESCENT;
        }
        if (diffPerMinute.stream().limit(5).allMatch(d -> d > 0)) {
            final var maxDiff =
                    diffPerMinute.stream().mapToDouble(d -> d).limit(5).max().orElseThrow();
            return maxDiff > 3.0
                    ? DiveProfileSegmentType.ASCENT
                    : DiveProfileSegmentType.LIGHT_ASCENT;
        }
        if (prev.isDescent() && diffPerMinute.getFirst() < 0) {
            return prev;
        }
        if (prev.isAscent() && diffPerMinute.getFirst() > 0) {
            return prev;
        }
    }

    public static <T> Gatherer<T, ?, List<T>> slidingWithTail(final int window) {
        return Gatherer.ofSequential(
                () -> new ArrayDeque<T>(window),
                (deque, element, downstream) -> {
                    deque.addLast(element);
                    if (deque.size() < window) {
                        return true;
                    }
                    if (deque.size() > window) {
                        deque.removeFirst();
                    }
                    downstream.push(List.copyOf(deque));
                    return true;
                },
                (deque, downstream) -> {
                    while (!deque.isEmpty()) {
                        downstream.push(List.copyOf(deque));
                        deque.removeFirst();
                    }
                });
    }

    private static Stream<TimeDepth> getSecondTimeDepth(final List<DiveMeasurementWithId> l) {
        final var first = l.getFirst().measurement();
        final var last = l.getLast().measurement();
        final var between = Duration.between(first.time(), last.time()).toSeconds();
        return LongStream.iterate(0, i -> i < between, i -> i + 1)
                .mapToObj(
                        s -> {
                            final var d = s / (double) between;
                            return new TimeDepth(
                                    first.time().plusSeconds(s),
                                    first.depth() * (1 - d) + last.depth() * d);
                        });
    }
}

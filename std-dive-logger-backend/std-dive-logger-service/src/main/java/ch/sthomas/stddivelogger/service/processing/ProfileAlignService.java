package ch.sthomas.stddivelogger.service.processing;

import static ch.sthomas.stddivelogger.service.processing.ResamplingUtils.getResamplingInfo;
import static ch.sthomas.stddivelogger.service.processing.ResamplingUtils.resampleMeasurements;

import static org.apache.commons.lang3.compare.ComparableUtils.min;

import ch.sthomas.stddivelogger.model.dive.profile.AlignType;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.align.ResampledDiveMeasurement;

import org.apache.commons.lang3.tuple.Pair;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ProfileAlignService {

    private static final Duration MAX_ALIGN_CHECK = Duration.ofMinutes(15);
    private static final int MIN_OVERLAP_COUNT = 30;

    public static Instant alignProfilesAuto(
            final DiveProfile referenceProfile,
            final DiveProfile targetProfile,
            final AlignType type) {
        if (referenceProfile.measurements() == null || targetProfile.measurements() == null) {
            throw new UnsupportedOperationException();
        }
        final var resamplingInfo = getResamplingInfo(referenceProfile.measurements());
        final var sampleRate = resamplingInfo.sampleRate();
        final var resampledReference =
                resampleMeasurements(referenceProfile.measurements(), resamplingInfo);
        final var resampledTarget =
                resampleMeasurements(targetProfile.measurements(), resamplingInfo);

        final var baseOffset =
                Duration.between(
                        resampledReference.getFirst().time(), resampledTarget.getFirst().time());
        final var maxOffsetCount =
                Math.toIntExact(min(baseOffset, MAX_ALIGN_CHECK).dividedBy(sampleRate));
        final var minK =
                findMinimumAlignOffsetIdx(
                        type, maxOffsetCount, resampledReference, resampledTarget);
        final var offset = sampleRate.multipliedBy(minK);
        return resampledTarget.getFirst().time().plus(offset);
    }

    private static Integer findMinimumAlignOffsetIdx(
            final AlignType type,
            final int maxOffsetCount,
            final List<ResampledDiveMeasurement> resampledReference,
            final List<ResampledDiveMeasurement> resampledTarget) {
        return IntStream.iterate(-maxOffsetCount, k -> k <= maxOffsetCount, k -> k + 1)
                .mapToObj(k -> Pair.of(k, getScore(k, resampledReference, resampledTarget, type)))
                .filter(
                        p ->
                                p.getValue() != null
                                        && !p.getValue().isInfinite()
                                        && !p.getValue().isNaN())
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Could not align profiles, no valid overlap range"));
    }

    private static double getScore(
            final int k,
            final List<ResampledDiveMeasurement> resampledReference,
            final List<ResampledDiveMeasurement> resampledTarget,
            final AlignType type) {
        final var refStart = Math.max(0, k);
        final var refEnd = Math.min(resampledReference.size(), resampledTarget.size() + k);
        final var length = refEnd - refStart;
        if (length <= MIN_OVERLAP_COUNT) {
            return Double.POSITIVE_INFINITY;
        }
        var acc = 0.0;

        for (var i = refStart; i < refEnd; i++) {
            final var d = resampledReference.get(i).depth() - resampledTarget.get(i - k).depth();

            switch (type) {
                case AUTO_MIN_AVG_DISTANCE -> acc += Math.abs(d);
                case AUTO_MIN_AVG_SQ_DISTANCE -> acc += d * d;
                case AUTO_MIN_MAX_DISTANCE -> acc = Math.max(acc, Math.abs(d));
                case MANUAL -> {}
            }
        }
        return switch (type) {
            case AUTO_MIN_AVG_DISTANCE, AUTO_MIN_AVG_SQ_DISTANCE -> acc / length;
            case AUTO_MIN_MAX_DISTANCE -> acc;
            case MANUAL -> throw new UnsupportedOperationException();
        };
    }
}

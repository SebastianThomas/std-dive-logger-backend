package ch.sthomas.stddivelogger.model.dive.profile;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveProfileSummary(
        Instant start,
        Instant end,
        double averageDepth,
        double maxDepth,
        @Nullable Duration surfaceInterval,
        Duration bottomTime,
        @Nullable Duration descentTime,
        @Nullable Duration ascentTime,
        @Nullable Double avgAscentRate,
        @Nullable Double startN2,
        @Nullable Double endN2,
        @Nullable Double o2Toxicity,
        @Nullable Double startCNS,
        @Nullable Double endCNS) {}

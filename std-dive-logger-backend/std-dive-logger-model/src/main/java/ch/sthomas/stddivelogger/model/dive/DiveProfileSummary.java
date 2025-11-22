package ch.sthomas.stddivelogger.model.dive;

import java.time.Duration;
import java.time.Instant;

public record DiveProfileSummary(
        Instant start,
        Instant end,
        double averageDepth,
        double maxDepth,
        Duration surfaceInterval,
        Duration bottomTime,
        Duration descentTime,
        Duration ascentTime,
        double avgAscentRate,
        double startN2,
        double endN2,
        double o2Toxicity,
        double startCNS,
        double endCNS) {}

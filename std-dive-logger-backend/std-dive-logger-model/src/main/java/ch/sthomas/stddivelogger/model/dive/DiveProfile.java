package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

import java.time.Instant;
import java.util.List;

public record DiveProfile(
        long id,
        DiveComputer diveComputer,
        Instant start,
        Instant end,
        List<DiveMeasurementWithId> measurements,
        @Nullable DiveProfileSummary summary) {}

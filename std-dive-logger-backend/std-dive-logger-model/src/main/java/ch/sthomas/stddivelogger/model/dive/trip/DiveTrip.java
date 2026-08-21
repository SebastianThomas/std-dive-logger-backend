package ch.sthomas.stddivelogger.model.dive.trip;

import ch.sthomas.stddivelogger.model.dive.TeamTerminology;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record DiveTrip(
        long id,
        String name,
        DiveTripType type,
        long ownerUserId,
        @Nullable TeamTerminology teamTerminology,
        Instant createdAt) {}

package ch.sthomas.stddivelogger.model.dive;

import java.time.Instant;
import java.util.List;

public record DiveProfile(
        long id,
        DiveComputer diveComputer,
        Instant start,
        Instant end,
        List<DiveMeasurement> measurements) {}

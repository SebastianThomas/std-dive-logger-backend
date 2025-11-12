package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

import java.util.List;

public record Dive(
        long id,
        int number,
        @Nullable String customIdentifier,
        DiveSite site,
        List<DiveProfile> profiles) {}

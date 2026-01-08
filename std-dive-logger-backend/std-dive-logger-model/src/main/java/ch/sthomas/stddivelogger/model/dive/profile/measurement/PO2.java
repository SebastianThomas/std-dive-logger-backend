package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import jakarta.annotation.Nullable;

public record PO2(
        @Nullable Double maxSetPoint, @Nullable Double measured, @Nullable Double calculated) {}

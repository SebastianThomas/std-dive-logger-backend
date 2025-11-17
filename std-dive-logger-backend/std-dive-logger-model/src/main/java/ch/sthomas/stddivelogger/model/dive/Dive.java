package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record Dive(
        long id,
        int number,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable DiveSite site,
        @NotNull List<DiveProfile> profiles,
        @NotNull List<Dive> buddiesDives,
        @NotNull List<String> namedBuddies) {}

package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SimplifiedDive(
        long id,
        int number,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable DiveSite site,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies) {}

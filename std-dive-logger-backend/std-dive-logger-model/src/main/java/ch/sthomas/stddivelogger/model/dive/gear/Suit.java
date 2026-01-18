package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.user.User;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record Suit(
        Long id,
        long userId,
        @NotNull SuitType type,
        @Nullable Double thickness,
        @NotNull String notes) {
    public static Suit createUnknown(final User user) {
        return new Suit(null, user.id(), SuitType.OTHER, null, "");
    }
}

package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;
import jakarta.validation.constraints.NotNull;

public record Suit(
        @Nullable Long id,
        long userId,
        @NotNull SuitType type,
        @Nullable Double thickness,
        @NotNull String notes) {
    public static Suit createUnknown(final User user) {
        return new Suit(null, user.id(), SuitType.OTHER, null, "");
    }
}

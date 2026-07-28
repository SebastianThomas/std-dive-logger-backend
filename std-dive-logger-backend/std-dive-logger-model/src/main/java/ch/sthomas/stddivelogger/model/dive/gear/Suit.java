package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.user.User;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

public record Suit(
        @Nullable @Positive Long id,
        @Positive long userId,
        @NotNull SuitType type,
        @Nullable @Positive Double thickness,
        @NotNull String notes) {
    public static Suit createUnknown(final User user) {
        return new Suit(null, user.id(), SuitType.OTHER, null, "");
    }
}

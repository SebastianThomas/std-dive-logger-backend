package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.user.User;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

public record Suit(
        @Nullable @Positive Long id,
        @Positive long userId,
        // null means "not specified" - distinct from SuitType.NONE, which means the diver
        // actually wore no exposure suit at all.
        @Nullable SuitType type,
        @Nullable @Positive Double thickness,
        @NotNull String notes) {
    public static Suit createUnknown(final User user) {
        return new Suit(null, user.id(), null, null, "");
    }
}

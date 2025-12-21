package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record Suit(
        Long id, @NotNull SuitType type, @Nullable Double thickness, @NotNull String notes) {}

package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

public record Visibility(
        @Nullable Double meters,
        @Nullable String description,
        @Nullable VisibilityFeeling feeling) {
    public static final Visibility EMPTY = new Visibility(null, "", null);

    public Visibility {
        if (meters != null && meters < 0) {
            throw new IllegalArgumentException("Visibility cannot be < 0");
        }
    }
}

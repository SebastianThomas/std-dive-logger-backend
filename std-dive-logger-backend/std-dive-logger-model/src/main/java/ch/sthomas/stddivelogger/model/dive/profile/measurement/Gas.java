package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import jakarta.annotation.Nullable;

public record Gas(
        double o2,
        double n2,
        double he,
        double h2,
        @Nullable CylinderSize size,
        @Nullable GasContent content,
        @Nullable String description) {
    public static final Gas AIR = new Gas(20.9);

    public Gas {
        final var sum = o2 + n2 + he + h2;
        if (sum < 0.99 || sum > 1.01) {
            throw new IllegalArgumentException("Gas must consist of 100%");
        }
    }

    public Gas(final double o2, final double he) {
        this(o2, 1 - o2 - he, he, 0.0, null, null, null);
    }

    public Gas(
            final double o2, final double he, final CylinderSize size, final String description) {
        this(o2, 1 - o2 - he, he, 0.0, size, null, description);
    }

    public Gas(
            final double o2,
            final double he,
            final CylinderSize size,
            final GasContent content,
            final String description) {
        this(o2, 1 - o2 - he, he, 0.0, size, content, description);
    }

    public Gas(final double o2) {
        this(o2, 0.0);
    }

    public Gas withContent(final GasContent content) {
        return new Gas(o2, n2, he, h2, size, content, description);
    }
}

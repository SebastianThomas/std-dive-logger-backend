package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import org.jspecify.annotations.Nullable;

public record Gas(
        double o2,
        double n2,
        double he,
        double h2,
        @Nullable CylinderSize size,
        @Nullable GasContent content,
        @Nullable String description) {
    public static final Gas AIR = new Gas(0.209);

    public Gas {
        final var sum = o2 + n2 + he + h2;
        if (sum < 0.99 || sum > 1.01) {
            throw new IllegalArgumentException("Gas must consist of 100%");
        }
        // The sum check alone doesn't catch every nonsensical mix: the 2-arg convenience
        // constructor below always computes n2 = 1 - o2 - he, so the sum is exactly 1 by
        // construction even when o2 + he alone is already over 100% - that just drives n2
        // negative instead. -0.01 tolerance matches the sum check's own 1% slack, so this only
        // rejects a genuinely negative component, not floating-point noise around zero.
        if (o2 < -0.01 || n2 < -0.01 || he < -0.01 || h2 < -0.01) {
            throw new IllegalArgumentException(
                    "Gas percentages cannot be negative - check that O2/He/N2/H2 together don't"
                            + " exceed 100%");
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
            @Nullable final CylinderSize size,
            @Nullable final GasContent content,
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

package ch.sthomas.stddivelogger.model.dive.measurement;

public record Gas(double o2, double n2, double he, double h2) {
    public static final Gas AIR = new Gas(20.9);

    public Gas {
        final var sum = o2 + n2 + he + h2;
        if (sum < 0.99 || sum > 1.01) {
            throw new IllegalArgumentException("Gas must consist of 100%");
        }
    }

    public Gas(final double o2, final double he) {
        this(o2, 1 - o2 - he, he, 0.0);
    }

    public Gas(final double o2) {
        this(o2, 0.0);
    }
}

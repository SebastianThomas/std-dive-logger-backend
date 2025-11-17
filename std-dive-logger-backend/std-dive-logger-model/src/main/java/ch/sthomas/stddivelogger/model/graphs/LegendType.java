package ch.sthomas.stddivelogger.model.graphs;

public enum LegendType {
    LEFT,
    RIGHT,
    NO_LEGEND;

    public double getX(final int width) {
        final var padding = 5;
        return switch (this) {
            case LEFT -> padding; // Left align required
            case RIGHT -> width - padding; // Right align required
            case NO_LEGEND -> throw new IllegalArgumentException("Cannot get X of no legend");
        };
    }
}

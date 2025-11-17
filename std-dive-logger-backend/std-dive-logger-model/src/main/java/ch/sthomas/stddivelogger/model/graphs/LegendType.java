package ch.sthomas.stddivelogger.model.graphs;

public enum LegendType {
    LEFT,
    RIGHT,
    NO_LEGEND;

    public double getX(
            final int width, final int stringWidth, final int padding, final int textPadding) {
        return switch (this) {
            case LEFT -> padding - textPadding - stringWidth; // Left align required
            case RIGHT -> width - padding + textPadding; // Right align required
            case NO_LEGEND -> throw new IllegalArgumentException("Cannot get X of no legend");
        };
    }
}

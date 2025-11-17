package ch.sthomas.stddivelogger.model.graphs;

public enum LegendType {
    LEFT,
    RIGHT,
    NO_LEGEND;

    public double getX() {
        return switch (this) {
            case LEFT -> 0;
            case RIGHT -> 1;
            case NO_LEGEND -> throw new IllegalArgumentException("Cannot get X of no legend");
        };
    }
}

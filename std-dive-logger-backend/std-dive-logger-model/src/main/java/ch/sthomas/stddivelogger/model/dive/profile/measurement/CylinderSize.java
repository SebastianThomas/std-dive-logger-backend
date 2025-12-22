package ch.sthomas.stddivelogger.model.dive.profile.measurement;

public record CylinderSize(CylinderSizeUnit unit, double value) {
    public double liters() {
        return switch (unit) {
            case LITER -> value;
            case CUFT -> 28.31682 * value;
        };
    }
}

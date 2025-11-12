package ch.sthomas.stddivelogger.model.dive.measurement;

public record Temperature(double value, TemperatureUnit unit) {
    public double celsius() {
        return switch (unit) {
            case CELSIUS -> value;
        };
    }

    public enum TemperatureUnit {
        CELSIUS;
    }
}

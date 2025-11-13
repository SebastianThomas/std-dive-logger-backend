package ch.sthomas.stddivelogger.model.dive.measurement;

public record Temperature(double value, TemperatureUnit unit) {
    public Temperature asCelsius() {
        return new Temperature(celsius(), TemperatureUnit.CELSIUS);
    }

    public double celsius() {
        return switch (unit) {
            case CELSIUS -> value;
            case KELVIN -> value - 273.15;
        };
    }

    public enum TemperatureUnit {
        CELSIUS,
        KELVIN;
    }
}

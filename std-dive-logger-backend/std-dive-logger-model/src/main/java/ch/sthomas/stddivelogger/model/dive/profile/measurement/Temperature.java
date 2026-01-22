package ch.sthomas.stddivelogger.model.dive.profile.measurement;

public record Temperature(double value, TemperatureUnit unit) {
    public Temperature asCelsius() {
        return new Temperature(celsius(), TemperatureUnit.CELSIUS);
    }

    public double celsius() {
        return switch (unit) {
            case CELSIUS -> value;
            case KELVIN -> value - 273.15;
            case FAHRENHEIT -> 1.8 * value - 32;
        };
    }

    public enum TemperatureUnit {
        CELSIUS,
        KELVIN,
        FAHRENHEIT;
    }
}

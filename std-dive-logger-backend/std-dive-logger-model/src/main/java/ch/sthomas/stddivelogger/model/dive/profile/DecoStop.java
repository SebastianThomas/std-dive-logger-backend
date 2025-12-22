package ch.sthomas.stddivelogger.model.dive.profile;

import java.time.Duration;

public record DecoStop(String type, double depth, long seconds) {
    public Duration duration() {
        return Duration.ofMinutes(seconds);
    }
}

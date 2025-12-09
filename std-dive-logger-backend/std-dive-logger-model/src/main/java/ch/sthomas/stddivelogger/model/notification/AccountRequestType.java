package ch.sthomas.stddivelogger.model.notification;

import java.time.Duration;

public enum AccountRequestType {
    LOGIN,
    CHANGE_PASSWORD,
    VERIFY_EMAIL;

    public Duration getValidDuration() {
        return switch (this) {
            case LOGIN -> Duration.ofHours(1);
            case CHANGE_PASSWORD -> Duration.ofHours(12);
            case VERIFY_EMAIL -> Duration.ofDays(2);
        };
    }
}

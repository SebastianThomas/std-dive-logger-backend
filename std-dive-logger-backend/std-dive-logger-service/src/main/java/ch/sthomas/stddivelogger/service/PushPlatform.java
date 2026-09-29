package ch.sthomas.stddivelogger.service;

import java.net.URI;
import java.util.Locale;

/**
 * Which push service a subscription belongs to, by its endpoint host. Apple (Safari / iOS home
 * screen apps) revokes a subscription after a few pushes that show no notification, so a silent
 * data push must still show one there; Chromium and Firefox tolerate occasional silent pushes.
 */
public enum PushPlatform {
    APPLE,
    OTHER;

    public static PushPlatform of(final String endpoint) {
        try {
            final var host = URI.create(endpoint).getHost();
            if (host != null && host.toLowerCase(Locale.ROOT).endsWith("push.apple.com")) {
                return APPLE;
            }
        } catch (final IllegalArgumentException e) {
            // Not a URI at all: treat as the lenient default, the send will fail on its own.
        }
        return OTHER;
    }

    public boolean requiresVisibleNotification() {
        return this == APPLE;
    }
}

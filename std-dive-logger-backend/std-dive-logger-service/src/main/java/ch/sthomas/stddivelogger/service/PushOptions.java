package ch.sthomas.stddivelogger.service;

import nl.martijndwars.webpush.Urgency;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Per-kind Web Push delivery headers (RFC 8030): how long the push service keeps an undelivered
 * message, its urgency, and a {@code topic} - a pending message with the same topic is replaced by
 * the newer one, so an offline device only ever receives the latest.
 */
public record PushOptions(Duration ttl, Urgency urgency, @Nullable String topic) {

    public static final PushOptions REMINDER =
            new PushOptions(Duration.ofDays(1), Urgency.NORMAL, null);

    // NORMAL, not LOW: LOW waits for Wi-Fi or power, and the point is to arrive before the boat.
    public static final PushOptions LOGBOOK_SYNC =
            new PushOptions(Duration.ofDays(3), Urgency.NORMAL, "logbook-sync");
}

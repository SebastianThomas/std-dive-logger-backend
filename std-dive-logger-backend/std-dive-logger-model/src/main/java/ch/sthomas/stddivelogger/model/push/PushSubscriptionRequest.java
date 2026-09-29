package ch.sthomas.stddivelogger.model.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

/**
 * The browser's {@code PushSubscription.toJSON()} shape, POSTed to {@code /v1/push/subscriptions}
 * when the user enables reminders on a device. {@code expirationTime} is ignored (always null for
 * VAPID web push today). {@code logbookSync} null keeps the stored choice (true for a new device).
 */
public record PushSubscriptionRequest(
        @NotBlank String endpoint, @NotNull @Valid Keys keys, @Nullable Boolean logbookSync) {

    public PushSubscriptionRequest(final String endpoint, final Keys keys) {
        this(endpoint, keys, null);
    }

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
}

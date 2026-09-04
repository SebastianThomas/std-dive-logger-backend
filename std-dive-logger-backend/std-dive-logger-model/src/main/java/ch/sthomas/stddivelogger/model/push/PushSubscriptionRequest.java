package ch.sthomas.stddivelogger.model.push;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The browser's {@code PushSubscription.toJSON()} shape, POSTed to {@code /v1/push/subscriptions}
 * when the user enables reminders on a device. {@code expirationTime} is ignored (always null for
 * VAPID web push today).
 */
public record PushSubscriptionRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}
}

package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;

/**
 * Delivers one payload to one browser subscription over the Web Push protocol (RFC 8030 + VAPID,
 * RFC 8291 payload encryption). Implemented by {@code VapidWebPushSender}. Must never throw and
 * must be safe to call concurrently.
 */
public interface WebPushSender {

    PushSendResult send(PushSubscriptionEntity subscription, byte[] payload, PushOptions options);

    /** Whether a real sender is wired up and configured. */
    boolean isConfigured();
}

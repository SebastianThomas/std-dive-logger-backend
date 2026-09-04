package ch.sthomas.stddivelogger.service;

import ch.sthomas.stddivelogger.model.entity.PushSubscriptionEntity;
import ch.sthomas.stddivelogger.model.push.PushSendResult;
import ch.sthomas.stddivelogger.model.push.WebPushMessage;

/**
 * Delivers one {@link WebPushMessage} to one browser subscription over the Web Push protocol (RFC
 * 8030 + VAPID, RFC 8291 payload encryption). Implemented by {@code VapidWebPushSender}.
 */
public interface WebPushSender {

    PushSendResult send(PushSubscriptionEntity subscription, WebPushMessage message);

    /** Whether a real sender is wired up and configured. */
    boolean isConfigured();
}

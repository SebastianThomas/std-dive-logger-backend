package ch.sthomas.stddivelogger.model.push;

/** Outcome of trying to deliver one web push to one subscription. */
public enum PushSendResult {
    /** Accepted by the push service. */
    SENT,
    /** The endpoint is gone (HTTP 404/410) - the subscription should be deleted. */
    GONE,
    /** A transient failure - keep the subscription, bump its failure count. */
    FAILED,
    /** Push isn't configured (no VAPID keys) - nothing was attempted. */
    NOT_CONFIGURED
}

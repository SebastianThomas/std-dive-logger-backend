-- Web Push subscriptions: one row per browser a user has enabled reminders on (the endpoint URL
-- plus the two client keys from PushSubscription.toJSON()). The analytics deployable reads these
-- when it has a due reminder to push.
--
-- TODO(push): the actual VAPID-signed, encrypted send is not wired yet - see WebPushSender and
-- .claude/PUSH_SETUP.md. This table + the subscribe/unsubscribe endpoints are ready so the
-- frontend opt-in flow can be built and tested against real rows in the meantime.

CREATE TABLE t_push_subscription
(
    pk_push_subscription_id BIGSERIAL,
    fk_user_id              INTEGER                  NOT NULL
        REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    endpoint                TEXT                     NOT NULL,
    p256dh                  TEXT                     NOT NULL,
    auth                    TEXT                     NOT NULL,
    user_agent              TEXT,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at         TIMESTAMP WITH TIME ZONE,
    failure_count           INTEGER                  NOT NULL DEFAULT 0,
    PRIMARY KEY (pk_push_subscription_id),
    UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscription_user ON t_push_subscription (fk_user_id);

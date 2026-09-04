-- Time-sensitive home-page prompts: dive anniversaries ("3 years ago today ...") and the dynamic
-- "time to go diving again" nudge. Computed + upserted by the analytics deployable, recomputed
-- when the day rolls over (anniversaries move) or the diver's dives change. ws reads the
-- currently-relevant, not-dismissed rows for GET /v1/home; a nightly job deletes expired rows.

CREATE TABLE t_diver_reminder
(
    pk_reminder_id BIGSERIAL,
    fk_diver_id    INTEGER                  NOT NULL
        REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    kind           TEXT                     NOT NULL,
    dedupe_key     TEXT                     NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    relevant_on    DATE                     NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    title          TEXT                     NOT NULL,
    body           TEXT                     NOT NULL,
    dive_id        INTEGER,
    years_ago      INTEGER,
    pushable       BOOLEAN                  NOT NULL DEFAULT TRUE,
    dismissed_at   TIMESTAMP WITH TIME ZONE,
    push_sent_at   TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (pk_reminder_id),
    UNIQUE (fk_diver_id, dedupe_key)
);

-- The read path: a diver's live, not-dismissed reminders.
CREATE INDEX idx_diver_reminder_active
    ON t_diver_reminder (fk_diver_id, expires_at)
    WHERE dismissed_at IS NULL;

-- The push path: rows that still need a push sent.
CREATE INDEX idx_diver_reminder_unpushed
    ON t_diver_reminder (relevant_on)
    WHERE push_sent_at IS NULL AND dismissed_at IS NULL AND pushable;

-- Per-diver bookkeeping: last recompute day + the dive fingerprint seen then (mirrors
-- t_diver_activity_stats' staleness check).
CREATE TABLE t_diver_reminder_run
(
    fk_diver_id        INTEGER                  NOT NULL PRIMARY KEY
        REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    computed_on        DATE                     NOT NULL,
    source_fingerprint TEXT                     NOT NULL,
    computed_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

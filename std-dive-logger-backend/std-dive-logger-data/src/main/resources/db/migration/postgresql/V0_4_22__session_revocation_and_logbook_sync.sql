-- Revocable sessions: every refresh token now belongs to a user, so a password change / "log out
-- on all devices" / account deletion can drop all of them. Existing rows can't be mapped to a user
-- (only jti + expiry were stored), so they are dropped: everyone signs in once after this deploy.
DELETE FROM t_refresh_tokens;

ALTER TABLE t_refresh_tokens
    ADD COLUMN fk_user_id INTEGER NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE;

CREATE INDEX idx_refresh_tokens_user ON t_refresh_tokens (fk_user_id);

-- Logbook sync push: per device opt-out, plus the last fingerprint pushed per diver (see
-- LogbookSyncDataService - a changed fingerprint, debounced, triggers one snapshot push).
ALTER TABLE t_push_subscription
    ADD COLUMN logbook_sync BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE t_logbook_sync_state
(
    fk_user_id  INTEGER PRIMARY KEY REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    fingerprint TEXT NOT NULL,
    sent_at     TIMESTAMP WITH TIME ZONE
);

-- Per-diver home-dashboard activity / trend stats, recomputed by the analytics deployable only
-- for divers whose dives changed since the last run (source_fingerprint). ws reads this row
-- straight through (and computes+stores it once on a cache miss). The whole computed blob is a
-- single jsonb value so adding a stat is a version bump, not a migration.
CREATE TABLE t_diver_activity_stats
(
    fk_diver_id        INTEGER                  NOT NULL PRIMARY KEY
        REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    computed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    computed_version   INTEGER                  NOT NULL,
    source_fingerprint TEXT                     NOT NULL,
    stats              JSONB                    NOT NULL
);

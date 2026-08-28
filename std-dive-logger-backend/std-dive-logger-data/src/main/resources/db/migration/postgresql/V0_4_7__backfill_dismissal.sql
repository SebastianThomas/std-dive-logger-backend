-- One (dive, reason) pair the user has marked "no more info to add" in the backfill guide. Kept as
-- its own table (not a flag on t_dives) so that when a new backfillable field is added later it can
-- be bulk-managed for old dives - e.g. a rollout migration doing
--   INSERT INTO t_dive_backfill_dismissal (fk_dive_id, reason)
--   SELECT pk_dive_id, 'NEW_REASON' FROM t_dives WHERE <doesn't apply> ON CONFLICT DO NOTHING;
-- A reason with no row here surfaces in the queue automatically. `reason` maps to the
-- DiveBackfillField enum (VISIBILITY / GAS_CONSUMPTION / WATER_TYPE / LEADER / NOTES / ...).
CREATE TABLE t_dive_backfill_dismissal
(
    pk_dive_backfill_dismissal_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id                    BIGINT      NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    reason                        VARCHAR(32) NOT NULL,
    dismissed_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dive_backfill_dismissal UNIQUE (fk_dive_id, reason)
);

CREATE INDEX ix_dive_backfill_dismissal_dive ON t_dive_backfill_dismissal (fk_dive_id);

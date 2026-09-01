-- "Highlight" (star) a dive so it surfaces on the home dashboard and can be filtered in the dive
-- list. A plain per-dive flag - no separate table, since a dive is highlighted or not with no
-- extra attributes.
ALTER TABLE t_dives
    ADD COLUMN highlighted BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index: the only query is "this diver's highlighted dives" (home dashboard + list
-- filter), a small subset of a small table, so index just those rows.
CREATE INDEX IF NOT EXISTS idx_dives_highlighted
    ON t_dives (fk_diver_id)
    WHERE highlighted;

-- Sections cut out of a profile (ProfileCuts): no samples, not dive time. Offsets from the profile
-- start, like its samples, so aligning the profile moves them along.
CREATE TABLE t_dive_profile_gap
(
    fk_dive_profile_id INTEGER  NOT NULL
        REFERENCES t_dive_profiles (pk_dive_profile_id) ON DELETE CASCADE,
    sort_order         INT      NOT NULL,
    start_offset       INTERVAL NOT NULL,
    end_offset         INTERVAL NOT NULL,
    PRIMARY KEY (fk_dive_profile_id, sort_order)
);

-- The cuts again, as [startMs, endMs] pairs relative to the same anchor as trim_*_offset_ms, so
-- re-processing a profile from its stored files re-applies them.
ALTER TABLE t_dive_profile_history
    ADD COLUMN trim_cuts_ms JSONB;

ALTER TABLE t_pending_import
    ADD COLUMN reimport_target_dive_id INTEGER REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    ADD COLUMN reimport_target_profile_id INTEGER REFERENCES t_dive_profiles (pk_dive_profile_id) ON DELETE CASCADE;

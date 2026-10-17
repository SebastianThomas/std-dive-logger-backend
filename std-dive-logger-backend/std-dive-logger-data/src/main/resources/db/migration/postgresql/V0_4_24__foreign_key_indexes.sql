-- Postgres never indexes a foreign-key column by itself. These are the ones read on hot paths -
-- loading one dive (profiles -> measurements, buddies, cylinders, access checks), the stats
-- queries joining measurements, and site / tag / equipment / group lookups - so each was a
-- sequential scan that grew with every dive logged by anyone (t_dive_measurements above all).
CREATE INDEX IF NOT EXISTS idx_dive_measurements_profile ON t_dive_measurements (fk_dive_profile_id);
CREATE INDEX IF NOT EXISTS idx_dive_profiles_dive ON t_dive_profiles (fk_dive_id);
CREATE INDEX IF NOT EXISTS idx_dive_profile_segments_profile ON t_dive_profile_segments (fk_dive_profile_id);

CREATE INDEX IF NOT EXISTS idx_dive_buddy_dive ON t_dive_buddy (fk_dive_id);
CREATE INDEX IF NOT EXISTS idx_dive_buddy_buddy_dive ON t_dive_buddy (fk_buddy_dive_id);
CREATE INDEX IF NOT EXISTS idx_dive_configuration_cylinder_dive ON t_dive_configuration_cylinder (fk_dive_id);
CREATE INDEX IF NOT EXISTS idx_dive_configuration_suit ON t_dive_configuration (fk_suit_id);
CREATE INDEX IF NOT EXISTS idx_dive_configuration_ccr_unit ON t_dive_configuration (fk_ccr_unit_id);
CREATE INDEX IF NOT EXISTS idx_dives_site ON t_dives (dive_site);
CREATE INDEX IF NOT EXISTS idx_dive_tags_tag ON t_dive_tags (fk_tag_id);

CREATE INDEX IF NOT EXISTS idx_dive_privileges_dive ON t_dive_privileges (fk_dive_id);
CREATE INDEX IF NOT EXISTS idx_dive_privileges_user ON t_dive_privileges (fk_user_id);
CREATE INDEX IF NOT EXISTS idx_dive_privileges_groups_group ON t_dive_privileges_groups (fk_group_id);
CREATE INDEX IF NOT EXISTS idx_group_member_group ON t_group_member (fk_group_id);
CREATE INDEX IF NOT EXISTS idx_group_member_user ON t_group_member (fk_user_id);

CREATE INDEX IF NOT EXISTS idx_dive_trip_member_member_trip
    ON t_dive_trip_member (fk_member_trip_id) WHERE fk_member_trip_id IS NOT NULL;

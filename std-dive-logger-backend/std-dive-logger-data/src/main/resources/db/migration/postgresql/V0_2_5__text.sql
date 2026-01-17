ALTER TABLE t_suits
    ALTER COLUMN type TYPE TEXT;

ALTER TABLE t_dive_configuration
    ALTER COLUMN base_configuration TYPE TEXT;

ALTER TABLE t_dive_profile_segments
    ALTER COLUMN type TYPE TEXT;

ALTER TABLE t_group_member
    ALTER COLUMN role TYPE TEXT;

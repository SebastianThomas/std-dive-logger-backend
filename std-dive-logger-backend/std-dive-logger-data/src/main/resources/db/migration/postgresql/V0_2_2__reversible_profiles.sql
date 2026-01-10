ALTER TABLE t_dives
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE t_dives
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE t_dive_profiles
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE t_dive_profiles
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

CREATE TABLE t_dive_profile_history
(
    fk_dive_profile_id INTEGER     NOT NULL REFERENCES t_dive_profiles (pk_dive_profile_id) ON DELETE CASCADE ON UPDATE CASCADE PRIMARY KEY,
    original_start     TIMESTAMPTZ NOT NULL,
    original_end       TIMESTAMPTZ NOT NULL,
    original_dive_id   INTEGER     NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL
);

INSERT INTO t_dive_profile_history (fk_dive_profile_id,
                                    original_start,
                                    original_end,
                                    original_dive_id,
                                    created_at,
                                    updated_at)
SELECT pk_dive_profile_id,
       dive_profile_start,
       dive_profile_end,
       fk_dive_id,
       created_at,
       updated_at
FROM t_dive_profiles;

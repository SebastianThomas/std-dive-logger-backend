-- Global, anonymous per-site aggregates for the "suggest a dive site" feature. Refreshed in bulk
-- by the analytics deployable; ws reads it read-only. No per-diver data - counts and averages only.
CREATE TABLE t_dive_site_stats
(
    fk_dive_site_id            INTEGER                  NOT NULL PRIMARY KEY
        REFERENCES t_dive_site (pk_dive_site_id) ON DELETE CASCADE,
    computed_at                TIMESTAMP WITH TIME ZONE NOT NULL,
    total_dives                INTEGER                  NOT NULL,
    distinct_divers            INTEGER                  NOT NULL,
    recent_dives_30d           INTEGER                  NOT NULL,
    recent_distinct_divers_30d INTEGER                  NOT NULL,
    avg_visibility_m           DOUBLE PRECISION,
    visibility_sample_size     INTEGER                  NOT NULL,
    avg_max_depth              DOUBLE PRECISION,
    min_max_depth              DOUBLE PRECISION,
    max_max_depth              DOUBLE PRECISION,
    highlighted_dives          INTEGER                  NOT NULL
);

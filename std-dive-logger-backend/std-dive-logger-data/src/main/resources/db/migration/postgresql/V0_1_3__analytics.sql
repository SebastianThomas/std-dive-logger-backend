CREATE TABLE t_analytics_depth_variance
(
    fk_profile_id        INTEGER          NOT NULL REFERENCES t_dive_profiles (pk_dive_profile_id),
    fk_measurement_start INTEGER          NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id),
    fk_measurement_end   INTEGER          NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id),
    avg_depth            DOUBLE PRECISION NOT NULL,
    max_depth            DOUBLE PRECISION NOT NULL,
    min_depth            DOUBLE PRECISION NOT NULL,
    deviation_avg        DOUBLE PRECISION NOT NULL,
    deviation_variance   DOUBLE PRECISION NOT NULL,
    deviation_01p        DOUBLE PRECISION NOT NULL,
    deviation_10p        DOUBLE PRECISION NOT NULL,
    deviation_median     DOUBLE PRECISION NOT NULL,
    deviation_90p        DOUBLE PRECISION NOT NULL,
    deviation_max        DOUBLE PRECISION GENERATED ALWAYS AS (GREATEST(ABS(max_depth - avg_depth), ABS(min_depth - avg_depth))) STORED,
    PRIMARY KEY (fk_profile_id, fk_measurement_start, fk_measurement_end)
);

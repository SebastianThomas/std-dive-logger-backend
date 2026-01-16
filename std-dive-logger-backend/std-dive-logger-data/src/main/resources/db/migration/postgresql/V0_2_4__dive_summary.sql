CREATE TABLE t_dive_summary
(
    fk_dive_id       INTEGER          NOT NULL PRIMARY KEY REFERENCES t_dives (pk_dive_id),
    dive_start       TIMESTAMPTZ      NOT NULL,
    dive_end         TIMESTAMPTZ      NOT NULL,
    max_depth        DOUBLE PRECISION NOT NULL,
    avg_depth        DOUBLE PRECISION NOT NULL,
    duration_seconds INTEGER          NOT NULL,
    CHECK (duration_seconds > 0)
);

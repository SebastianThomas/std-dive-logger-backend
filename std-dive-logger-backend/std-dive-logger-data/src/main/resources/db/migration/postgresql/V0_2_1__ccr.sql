CREATE TABLE t_measurement_po2
(
    fk_measurement_id INTEGER NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id),
    max_set_point     DOUBLE PRECISION,
    measured          DOUBLE PRECISION,
    calculated        DOUBLE PRECISION,
    CHECK (max_set_point IS NOT NULL OR measured IS NOT NULL OR calculated IS NOT NULL)
);
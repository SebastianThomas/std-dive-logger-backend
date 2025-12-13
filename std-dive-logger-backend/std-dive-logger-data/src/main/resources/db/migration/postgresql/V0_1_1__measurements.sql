ALTER TABLE t_dive_measurements
    ADD COLUMN IF NOT EXISTS rmv_liters DOUBLE PRECISION;

CREATE TABLE t_cylinder_size
(
    pk_cylinder_size_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    unit                VARCHAR(10)      NOT NULL,
    value               DOUBLE PRECISION NOT NULL,
    UNIQUE (unit, value)
);

CREATE TABLE t_gas_mix
(
    pk_gas_mix_id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    o2            DOUBLE PRECISION NOT NULL,
    n2            DOUBLE PRECISION NOT NULL,
    he            DOUBLE PRECISION NOT NULL DEFAULT 0,
    UNIQUE (o2, n2, he)
);

CREATE TABLE t_gas
(
    pk_gas_id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_gas_mix_id       INTEGER NOT NULL REFERENCES t_gas_mix (pk_gas_mix_id),
    fk_cylinder_size_id INTEGER REFERENCES t_cylinder_size (pk_cylinder_size_id),
    description         TEXT,
    content_value       DOUBLE PRECISION,
    content_unit        TEXT
);

ALTER TABLE t_dive_measurements
    ADD COLUMN fk_gas_id INTEGER REFERENCES t_gas (pk_gas_id);

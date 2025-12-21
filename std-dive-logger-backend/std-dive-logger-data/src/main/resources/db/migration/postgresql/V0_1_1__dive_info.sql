ALTER TABLE t_dives
    ADD COLUMN notes TEXT NOT NULL DEFAULT '';

CREATE TABLE t_dive_visibility
(
    fk_dive_id             INTEGER NOT NULL REFERENCES t_dives (pk_dive_id) PRIMARY KEY,
    visibility_meters      DOUBLE PRECISION,
    visibility_feeling     VARCHAR(10),
    visibility_description TEXT,
    CHECK (visibility_meters >= 0)
);

CREATE TABLE t_dive_gas_consumption
(
    fk_dive_id   INTEGER          NOT NULL REFERENCES t_dives (pk_dive_id) PRIMARY KEY,
    sac_bar      DOUBLE PRECISION NOT NULL,
    rmv_liters   DOUBLE PRECISION NOT NULL,
    total_liters DOUBLE PRECISION NOT NULL
);

CREATE TABLE t_suits
(
    pk_suit_id       INTEGER     NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type             VARCHAR(10) NOT NULL,
    thickness_MM     DOUBLE PRECISION,
    additional_notes TEXT        NOT NULL DEFAULT ''
);

CREATE TABLE t_dive_configuration
(
    fk_dive_id         INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id) PRIMARY KEY,
    fk_suit_id         INTEGER     NOT NULL REFERENCES t_suits (pk_suit_id),
    base_configuration VARCHAR(10) NOT NULL,
    weight_kg          DOUBLE PRECISION,
    weight_feeling     VARCHAR(10)
);

CREATE TABLE t_dive_configuration_cylinder
(
    pk_configuration_cylinder_id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id                   INTEGER NOT NULL REFERENCES t_dive_configuration (fk_dive_id),
    fk_cylinder_size_id          INTEGER NOT NULL REFERENCES t_cylinder_size (pk_cylinder_size_id),
    start_bar                    DOUBLE PRECISION,
    end_bar                      DOUBLE PRECISION,
    notes                        TEXT    NOT NULL
);

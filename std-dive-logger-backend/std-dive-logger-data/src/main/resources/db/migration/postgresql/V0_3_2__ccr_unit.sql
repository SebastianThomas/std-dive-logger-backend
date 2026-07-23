CREATE TABLE t_ccr_units
(
    pk_ccr_unit_id   INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id       INTEGER NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    name             TEXT    NOT NULL,
    additional_notes TEXT    NOT NULL DEFAULT '',
    is_public        BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE t_dive_configuration
    ADD COLUMN fk_ccr_unit_id INTEGER REFERENCES t_ccr_units (pk_ccr_unit_id);

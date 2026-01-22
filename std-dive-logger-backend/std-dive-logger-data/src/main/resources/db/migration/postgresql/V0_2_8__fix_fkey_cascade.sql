ALTER TABLE t_dive_visibility
    DROP CONSTRAINT t_dive_visibility_fk_dive_id_fkey;

ALTER TABLE t_dive_visibility
    ADD CONSTRAINT t_dive_visibility_fk_dive_id_fkey
        FOREIGN KEY (fk_dive_id)
            REFERENCES t_dives (pk_dive_id)
            ON DELETE CASCADE;

ALTER TABLE t_dive_gas_consumption
    DROP CONSTRAINT t_dive_gas_consumption_fk_dive_id_fkey;

ALTER TABLE t_dive_gas_consumption
    ADD CONSTRAINT t_dive_gas_consumption_fk_dive_id_fkey
        FOREIGN KEY (fk_dive_id)
            REFERENCES t_dives (pk_dive_id)
            ON DELETE CASCADE;

ALTER TABLE t_dive_configuration
    DROP CONSTRAINT t_dive_configuration_fk_dive_id_fkey;

ALTER TABLE t_dive_configuration
    ADD CONSTRAINT t_dive_configuration_fk_dive_id_fkey
        FOREIGN KEY (fk_dive_id)
            REFERENCES t_dives (pk_dive_id)
            ON DELETE CASCADE;

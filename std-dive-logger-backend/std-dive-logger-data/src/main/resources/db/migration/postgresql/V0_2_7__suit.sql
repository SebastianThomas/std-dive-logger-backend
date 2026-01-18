ALTER TABLE t_dive_configuration
    ALTER CONSTRAINT t_dive_configuration_fk_suit_id_fkey
        DEFERRABLE INITIALLY DEFERRED;

BEGIN;
SET CONSTRAINTS ALL DEFERRED;
TRUNCATE t_suits CASCADE;
ALTER TABLE t_suits
    ADD COLUMN fk_user_id INTEGER NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE;
INSERT INTO t_suits (fk_user_id, type, thickness_mm, additional_notes)
SELECT pk_user_id, 'OTHER', NULL, 'Unknown (Default)'
FROM t_users;
INSERT INTO t_dive_configuration (fk_dive_id, fk_suit_id, base_configuration, weight_kg, weight_feeling)
SELECT pk_dive_id, pk_suit_id, 'OTHER', NULL, NULL
FROM t_dives d
         INNER JOIN t_suits s ON d.fk_diver_id = s.fk_user_id;
COMMIT;

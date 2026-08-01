-- toEntity(Gas) looks up an existing t_gas row by composition and inserts one if missing, with no
-- DB-level backstop against two concurrent requests both seeing "not found" and both inserting -
-- silently splitting one gas composition across two indistinguishable rows. A unique constraint
-- closes that race by making the DB itself reject the loser's insert (the application layer
-- catches this and re-reads the winner's row). Unlike t_suits/t_ccr_units, t_gas is not
-- user-scoped - it's a shared/global table - so the composition alone defines a duplicate.
--
-- Before adding it: any existing duplicates (same gas mix/cylinder size/description/content) are
-- true duplicates by definition of the columns being constrained, so consolidating them -
-- repointing any dive measurement that referenced a dropped duplicate onto the kept row - is
-- lossless. t_dive_measurements is the only table referencing t_gas.

WITH gas_duplicates AS (
    SELECT pk_gas_id,
           MIN(pk_gas_id) OVER (
               PARTITION BY fk_gas_mix_id, fk_cylinder_size_id, description, content_value,
                            content_unit
           ) AS keep_id
    FROM t_gas
),
     gas_to_remove AS (
         SELECT pk_gas_id, keep_id FROM gas_duplicates WHERE pk_gas_id <> keep_id
     )
UPDATE t_dive_measurements dm
SET fk_gas_id = gas_to_remove.keep_id
FROM gas_to_remove
WHERE dm.fk_gas_id = gas_to_remove.pk_gas_id;

DELETE
FROM t_gas g USING (
    SELECT pk_gas_id,
           MIN(pk_gas_id) OVER (
               PARTITION BY fk_gas_mix_id, fk_cylinder_size_id, description, content_value,
                            content_unit
           ) AS keep_id
    FROM t_gas
) dups
WHERE g.pk_gas_id = dups.pk_gas_id
  AND dups.pk_gas_id <> dups.keep_id;

ALTER TABLE t_gas
    ADD CONSTRAINT t_gas_composition_key
        UNIQUE (fk_gas_mix_id, fk_cylinder_size_id, description, content_value, content_unit);

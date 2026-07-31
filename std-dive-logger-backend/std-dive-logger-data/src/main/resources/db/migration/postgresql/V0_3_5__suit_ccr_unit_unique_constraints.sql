-- findOrCreateSuit/findOrCreateCcrUnit look up an existing row by (user, composition) and insert
-- one if missing, with no DB-level backstop against two concurrent requests both seeing "not
-- found" and both inserting - silently splitting one piece of gear across two indistinguishable
-- rows. A unique constraint closes that race by making the DB itself reject the loser's insert
-- (the application layer catches this and re-reads the winner's row).
--
-- Before adding it: any existing duplicates (same user/type/thickness/notes, or same
-- user/name/notes) are true duplicates by definition of the columns being constrained, so
-- consolidating them - repointing any dive configuration that referenced a dropped duplicate onto
-- the kept row - is lossless. t_dive_configuration is the only table referencing either.

WITH suit_duplicates AS (
    SELECT pk_suit_id,
           MIN(pk_suit_id) OVER (
               PARTITION BY fk_user_id, type, thickness_mm, additional_notes
           ) AS keep_id
    FROM t_suits
),
     suit_to_remove AS (
         SELECT pk_suit_id, keep_id FROM suit_duplicates WHERE pk_suit_id <> keep_id
     )
UPDATE t_dive_configuration dc
SET fk_suit_id = suit_to_remove.keep_id
FROM suit_to_remove
WHERE dc.fk_suit_id = suit_to_remove.pk_suit_id;

DELETE
FROM t_suits s USING (
    SELECT pk_suit_id,
           MIN(pk_suit_id) OVER (
               PARTITION BY fk_user_id, type, thickness_mm, additional_notes
           ) AS keep_id
    FROM t_suits
) dups
WHERE s.pk_suit_id = dups.pk_suit_id
  AND dups.pk_suit_id <> dups.keep_id;

ALTER TABLE t_suits
    ADD CONSTRAINT t_suits_user_type_thickness_notes_key
        UNIQUE (fk_user_id, type, thickness_mm, additional_notes);

WITH ccr_unit_duplicates AS (
    SELECT pk_ccr_unit_id,
           MIN(pk_ccr_unit_id) OVER (
               PARTITION BY fk_user_id, name, additional_notes
           ) AS keep_id
    FROM t_ccr_units
),
     ccr_unit_to_remove AS (
         SELECT pk_ccr_unit_id, keep_id FROM ccr_unit_duplicates WHERE pk_ccr_unit_id <> keep_id
     )
UPDATE t_dive_configuration dc
SET fk_ccr_unit_id = ccr_unit_to_remove.keep_id
FROM ccr_unit_to_remove
WHERE dc.fk_ccr_unit_id = ccr_unit_to_remove.pk_ccr_unit_id;

DELETE
FROM t_ccr_units c USING (
    SELECT pk_ccr_unit_id,
           MIN(pk_ccr_unit_id) OVER (
               PARTITION BY fk_user_id, name, additional_notes
           ) AS keep_id
    FROM t_ccr_units
) dups
WHERE c.pk_ccr_unit_id = dups.pk_ccr_unit_id
  AND dups.pk_ccr_unit_id <> dups.keep_id;

ALTER TABLE t_ccr_units
    ADD CONSTRAINT t_ccr_units_user_name_notes_key
        UNIQUE (fk_user_id, name, additional_notes);

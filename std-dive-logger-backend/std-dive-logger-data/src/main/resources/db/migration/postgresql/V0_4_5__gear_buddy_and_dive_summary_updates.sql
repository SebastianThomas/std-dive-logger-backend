-- SuitType.OTHER was a meaningless catch-all (distinct from NONE, which genuinely means "no
-- exposure suit worn") - removed from the enum in favor of a real null meaning "not specified".
-- Existing rows that used it (including the one-per-user "Unknown (Default)" placeholder seeded
-- by V0_2_7__suit.sql) become null rather than failing to deserialize a value the app no longer
-- recognizes.
ALTER TABLE t_suits
    ALTER COLUMN type DROP NOT NULL;

UPDATE t_suits
SET type = NULL
WHERE type = 'OTHER';

-- A manually-entered dive's synthetic surface/max-depth/surface profile isn't a real depth-time
-- curve - averaging it produced a fabricated, low-balled number (roughly max_depth/3). avg_depth
-- is now genuinely unknown for such a dive unless the diver explicitly provides it (see
-- DiveSummaryEntity#setAverageDepth / UpdateDiveBody#averageDepth), so it must be nullable.
ALTER TABLE t_dive_summary
    ALTER COLUMN avg_depth DROP NOT NULL;

-- Clear the already-fabricated value for every existing manual-entry dive (identified the same
-- way DiveSummaryEntity#isManualEntryDive does: its one and only profile is on the synthetic
-- "Manual" computer) so it reads as genuinely unknown rather than keeping the wrong number until
-- the diver happens to re-save it.
UPDATE t_dive_summary s
SET avg_depth = NULL
WHERE s.fk_dive_id IN (
    SELECT p.fk_dive_id
    FROM t_dive_profiles p
             JOIN t_dive_computer c ON c.pk_dive_computer_id = p.fk_dive_computer
             JOIN t_computer_manufacturer m ON m.pk_manufacturer_id = c.fk_manufacturer_id
    WHERE m.name = 'Manual'
    GROUP BY p.fk_dive_id
    HAVING COUNT(*) = 1
);

-- BaseConfiguration.CHESTMOUNT_CCR was ambiguous about where the unit's bailout cylinder(s) are
-- rigged (unlike back/side mount CCR, where the name itself already implies it) - split into
-- CHESTMOUNT_CCR_SIDEMOUNT_BAILOUT / CHESTMOUNT_CCR_BACKMOUNT_BAILOUT. Existing rows can't be
-- guessed correctly either way, so they fall back to the existing OTHER catch-all rather than a
-- silently-wrong specific choice - affected divers can re-pick the right one.
UPDATE t_dive_configuration
SET base_configuration = 'OTHER'
WHERE base_configuration = 'CHESTMOUNT_CCR';

UPDATE t_ccr_units
SET default_base_configuration = 'OTHER'
WHERE default_base_configuration = 'CHESTMOUNT_CCR';

-- Merges DiveComputer rows that are really the same physical device, reported under two
-- different manufacturer-name spellings by different import paths for the same real company -
-- e.g. "Shearwater" (hardcoded by the native Shearwater XML/DL7 readers) vs "Shearwater
-- Research, Inc" (the company's own full name, as it appears in that same device's UDDF export).
-- Same user, same serial number, one manufacturer name (trimmed, case-insensitive) contained in
-- the other - the shorter/abbreviated spelling's computer is folded into the longer/fuller one's.
--
-- t_dive_profiles.fk_dive_computer is ON DELETE CASCADE, so every affected profile must be
-- repointed at the surviving computer *before* the duplicate row is deleted, or its dives would
-- be destroyed outright rather than merged.
CREATE TEMP TABLE tmp_dive_computer_merge AS
SELECT short.pk_dive_computer_id AS duplicate_id,
       long.pk_dive_computer_id  AS primary_id
FROM t_computer_manufacturer a
         JOIN t_computer_manufacturer b
              ON a.pk_manufacturer_id <> b.pk_manufacturer_id
                  AND length(trim(a.name)) < length(trim(b.name))
                  AND position(lower(trim(a.name)) IN lower(trim(b.name))) > 0
         JOIN t_dive_computer short ON short.fk_manufacturer_id = a.pk_manufacturer_id
         JOIN t_dive_computer long
              ON long.fk_manufacturer_id = b.pk_manufacturer_id
                  AND long.fk_user_id = short.fk_user_id
                  AND long.serial_number = short.serial_number
WHERE short.serial_number IS NOT NULL
  AND short.serial_number <> '';

UPDATE t_dive_profiles p
SET fk_dive_computer = m.primary_id
FROM tmp_dive_computer_merge m
WHERE p.fk_dive_computer = m.duplicate_id;

DELETE
FROM t_dive_computer
WHERE pk_dive_computer_id IN (SELECT duplicate_id FROM tmp_dive_computer_merge);

DROP TABLE tmp_dive_computer_merge;

-- Lets a dive note a suit type with no specific saved Suit behind it at all (e.g. a one-off
-- rental) - see DiveConfiguration.adHocSuitType's own doc comment. Independent of the existing
-- (always-present) fk_suit_id link.
ALTER TABLE t_dive_configuration
    ADD COLUMN ad_hoc_suit_type VARCHAR(20);

-- Lets a diver save a default BuddyRole per buddy (named or linked) that gets applied
-- automatically the first time that buddy is added to a dive - manually or via import.
-- Distinct from t_dive_trip_default_team (a per-trip roster) and the existing "apply role to
-- all dives" bulk action (DiveDataService#setNamedBuddyRole/#setLinkedBuddyRoleForUser), which
-- only retroactively backfills already-existing dive-buddy rows and isn't persisted anywhere.
CREATE TABLE t_dive_buddy_default_role
(
    pk_id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id       INTEGER     NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    fk_buddy_user_id INTEGER REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    buddy_name       TEXT,
    role             VARCHAR(32) NOT NULL,
    CONSTRAINT chk_dive_buddy_default_role_exactly_one CHECK (
        (fk_buddy_user_id IS NOT NULL AND buddy_name IS NULL) OR
        (fk_buddy_user_id IS NULL AND buddy_name IS NOT NULL)
        ),
    CONSTRAINT uq_dive_buddy_default_role_named UNIQUE (fk_user_id, buddy_name),
    CONSTRAINT uq_dive_buddy_default_role_linked UNIQUE (fk_user_id, fk_buddy_user_id)
);

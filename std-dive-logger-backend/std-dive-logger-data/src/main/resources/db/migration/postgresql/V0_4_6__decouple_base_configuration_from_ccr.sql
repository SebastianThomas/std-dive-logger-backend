-- BaseConfiguration used to conflate two independent concerns: how the diver's OWN cylinders are
-- rigged (backmount vs sidemount) and whether/how many/how each CCR unit is mounted. Splitting
-- them: BaseConfiguration is now just the diver's own rig (backmount/sidemount, nullable - "not
-- specified" rather than a fake default), while each CcrUnit now carries its own mount position
-- (backmount/sidemount/chestmount), and a dive can reference up to two CCR units (dual rebreather
-- setups) rather than needing one combined enum value per possible pairing.
ALTER TABLE t_dive_configuration
    ALTER COLUMN base_configuration DROP NOT NULL;

-- Old CCR-flavored BaseConfiguration values no longer exist - a dive's CCR-ness now comes purely
-- from whether it references a CcrUnit, not from base_configuration. Best-effort translation of
-- the previous values into the new (diver-rig-only) meaning; anything that doesn't cleanly imply
-- a rig style is cleared to null rather than guessed.
UPDATE t_dive_configuration
SET base_configuration = 'BACKMOUNT'
WHERE base_configuration IN
      ('SINGLE_TANK', 'SINGLE_TANK_AVELO', 'BACKMOUNT_DOUBLES', 'BACKMOUNT_CCR', 'DUAL_CCR_BACKMOUNT');
UPDATE t_dive_configuration
SET base_configuration = 'SIDEMOUNT'
WHERE base_configuration IN ('SIDEMOUNT_CCR', 'DUAL_CCR_SIDEMOUNT');
UPDATE t_dive_configuration
SET base_configuration = NULL
WHERE base_configuration IN
      ('CHESTMOUNT_CCR_SIDEMOUNT_BAILOUT', 'CHESTMOUNT_CCR_BACKMOUNT_BAILOUT',
       'DUAL_CCR_BACKMOUNT_SIDEMOUNT', 'DUAL_CCR_BACKMOUNT_CHESTMOUNT',
       'DUAL_CCR_SIDEMOUNT_CHESTMOUNT', 'OTHER');

-- CcrUnit's own mount position - repurposed from the same column, which used to store a combined
-- BaseConfiguration value representing "what a dive using this unit should default its
-- base_configuration to". Best-effort carry the mount-style-implying values over; anything that
-- doesn't cleanly imply one is cleared.
ALTER TABLE t_ccr_units
    RENAME COLUMN default_base_configuration TO mount_position;
UPDATE t_ccr_units
SET mount_position = 'BACKMOUNT'
WHERE mount_position IN ('BACKMOUNT_CCR', 'DUAL_CCR_BACKMOUNT');
UPDATE t_ccr_units
SET mount_position = 'SIDEMOUNT'
WHERE mount_position IN ('SIDEMOUNT_CCR', 'DUAL_CCR_SIDEMOUNT');
UPDATE t_ccr_units
SET mount_position = 'CHESTMOUNT'
WHERE mount_position IN
      ('CHESTMOUNT_CCR_SIDEMOUNT_BAILOUT', 'CHESTMOUNT_CCR_BACKMOUNT_BAILOUT');
UPDATE t_ccr_units
SET mount_position = NULL
WHERE mount_position IN
      ('SINGLE_TANK', 'SINGLE_TANK_AVELO', 'BACKMOUNT_DOUBLES', 'DUAL_CCR_BACKMOUNT_SIDEMOUNT',
       'DUAL_CCR_BACKMOUNT_CHESTMOUNT', 'DUAL_CCR_SIDEMOUNT_CHESTMOUNT', 'OTHER');

-- A dive can now reference a second, independent CCR unit for genuine dual-rebreather setups -
-- each unit's own mount_position (above) determines how it's worn, so every combination (e.g. one
-- backmount + one sidemount) is representable without a dedicated enum value per pairing.
ALTER TABLE t_dive_configuration
    ADD COLUMN fk_secondary_ccr_unit_id INTEGER REFERENCES t_ccr_units (pk_ccr_unit_id);

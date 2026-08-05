-- Links a dive computer to the CCR unit it belongs to (most computers/handsets used with a
-- rebreather are permanently paired with one specific unit), so importing a dive recorded on that
-- computer can automatically infer the CCR unit (and, via its own default base configuration
-- below, the dive mode) without the diver re-picking it every time.
ALTER TABLE t_dive_computer
    ADD COLUMN fk_ccr_unit_id INTEGER REFERENCES t_ccr_units (pk_ccr_unit_id);

-- The base configuration (e.g. SIDEMOUNT_CCR) a CCR unit's dives normally use. Nullable: unset
-- until the diver confirms it once, at which point every future import through a computer linked
-- to this unit can infer the same dive mode automatically instead of leaving it as a guess.
ALTER TABLE t_ccr_units
    ADD COLUMN default_base_configuration TEXT;

-- Extends per-dive cylinder tracking with what's needed to compute real RMV: the gas mix actually
-- in that cylinder (O2/He fraction - N2 is implied), its role (a plain OC dive's only cylinder,
-- CCR diluent, CCR O2 supply, or CCR bailout gas), and optionally which stretch of the dive it was
-- actually breathed from (null means "the whole dive", covering the common single-cylinder case
-- with no extra data entry required).
ALTER TABLE t_dive_configuration_cylinder
    ADD COLUMN gas_o2       DOUBLE PRECISION NOT NULL DEFAULT 0.21,
    ADD COLUMN gas_he       DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN role         VARCHAR(16)      NOT NULL DEFAULT 'OC',
    ADD COLUMN usage_start  TIMESTAMPTZ,
    ADD COLUMN usage_end    TIMESTAMPTZ;

ALTER TABLE t_dive_configuration_cylinder
    ALTER COLUMN gas_o2 DROP DEFAULT,
    ALTER COLUMN gas_he DROP DEFAULT,
    ALTER COLUMN role DROP DEFAULT;

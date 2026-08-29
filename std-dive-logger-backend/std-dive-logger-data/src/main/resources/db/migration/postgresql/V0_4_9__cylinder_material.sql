-- Cylinder material (ALU / STEEL) - descriptive only, no consumption maths.
-- Nullable: a truly unknown legacy row may stay null; every new write gets a value from the entity
-- layer (explicit from the picker, or StandardCylinder.inferMaterial).
ALTER TABLE t_dive_configuration_cylinder
    ADD COLUMN material VARCHAR(8);

-- Seed the catalog water-volume sizes so a snapped cylinder points at a canonical row.
INSERT INTO t_cylinder_size (unit, value)
VALUES ('LITER', 3), ('LITER', 5), ('LITER', 7), ('LITER', 10), ('LITER', 12), ('LITER', 15),
       ('LITER', 18), ('LITER', 20), ('LITER', 24), ('LITER', 30), ('LITER', 5.5), ('LITER', 9),
       ('LITER', 11.1)
ON CONFLICT (unit, value) DO NOTHING;

-- Snap each tracked cylinder within 0.3 L of a catalog value onto that canonical size row and set
-- its material. 7 L is ambiguous (Steel + Alu both exist) - it resolves to ALU via the same
-- inferMaterial rule below (3.5 < 7 < 8.5), not an arbitrary catalog pick.
WITH catalog(liters, material) AS (
    VALUES (3::double precision, 'STEEL'), (5, 'STEEL'), (7, 'ALU'), (10, 'STEEL'), (12, 'STEEL'),
           (15, 'STEEL'), (18, 'STEEL'), (20, 'STEEL'), (24, 'STEEL'), (30, 'STEEL'),
           (5.5, 'ALU'), (9, 'ALU'), (11.1, 'ALU')
),
cyl AS (
    SELECT c.pk_configuration_cylinder_id AS id,
           CASE cs.unit WHEN 'LITER' THEN cs.value ELSE cs.value * 28.31682 END AS liters
    FROM t_dive_configuration_cylinder c
    JOIN t_cylinder_size cs ON cs.pk_cylinder_size_id = c.fk_cylinder_size_id
),
snap AS (
    SELECT cyl.id, cat.liters AS snap_liters, cat.material AS snap_material
    FROM cyl
    JOIN LATERAL (
        SELECT liters, material
        FROM catalog
        WHERE abs(catalog.liters - cyl.liters) <= 0.3
        ORDER BY abs(catalog.liters - cyl.liters)
        LIMIT 1
    ) cat ON true
),
resolved AS (
    SELECT snap.id, snap.snap_material,
           (SELECT pk_cylinder_size_id FROM t_cylinder_size
            WHERE unit = 'LITER' AND abs(value - snap.snap_liters) < 1e-6) AS size_id
    FROM snap
)
UPDATE t_dive_configuration_cylinder c
SET fk_cylinder_size_id = resolved.size_id,
    material = resolved.snap_material
FROM resolved
WHERE c.pk_configuration_cylinder_id = resolved.id
  AND resolved.size_id IS NOT NULL;

-- Infer material for everything still unset (odd sizes, cuft ratings): <= 3.5 L steel, 3.5-8.5 L
-- alu, >= 8.5 L steel, any CUFT alu. Mirrors StandardCylinder.inferMaterial.
UPDATE t_dive_configuration_cylinder c
SET material = CASE
        WHEN cs.unit = 'CUFT' THEN 'ALU'
        WHEN (CASE cs.unit WHEN 'LITER' THEN cs.value ELSE cs.value * 28.31682 END) <= 3.5 THEN 'STEEL'
        WHEN (CASE cs.unit WHEN 'LITER' THEN cs.value ELSE cs.value * 28.31682 END) < 8.5 THEN 'ALU'
        ELSE 'STEEL'
    END
FROM t_cylinder_size cs
WHERE cs.pk_cylinder_size_id = c.fk_cylinder_size_id
  AND c.material IS NULL;

-- Multiple usage windows per tracked cylinder, replacing the single usage_start/usage_end pair.
-- An empty window list means "used whenever the same-role windowed cylinders aren't" (the whole
-- dive when none are windowed) - see CylinderConsumptionCalculator.
CREATE TABLE t_dive_configuration_cylinder_usage_window
(
    fk_configuration_cylinder_id BIGINT NOT NULL
        REFERENCES t_dive_configuration_cylinder (pk_configuration_cylinder_id) ON DELETE CASCADE,
    sort_order                   INT    NOT NULL,
    window_start                 TIMESTAMPTZ,
    window_end                   TIMESTAMPTZ,
    PRIMARY KEY (fk_configuration_cylinder_id, sort_order)
);

-- Carry every existing single window across as window 0.
INSERT INTO t_dive_configuration_cylinder_usage_window
(fk_configuration_cylinder_id, sort_order, window_start, window_end)
SELECT pk_configuration_cylinder_id, 0, usage_start, usage_end
FROM t_dive_configuration_cylinder
WHERE usage_start IS NOT NULL OR usage_end IS NOT NULL;

ALTER TABLE t_dive_configuration_cylinder
    DROP COLUMN usage_start,
    DROP COLUMN usage_end;

-- Water type is a physical property of the dive site, not of each individual dive. Move the source
-- of truth onto t_dive_site; a dive keeps only an optional per-dive override (t_dive_conditions).
-- Mirrors site_type VARCHAR(32) from V0_4_1 - plain nullable text, no check constraint.
ALTER TABLE t_dive_site
    ADD COLUMN water_type VARCHAR(16);

-- Seed each site from the most common water type across dives already logged there, so the per-dive
-- values can be dropped without losing the underlying fact. Deterministic tiebreak on the enum name.
-- Join path: t_dive_conditions.fk_dive_id -> t_dives.pk_dive_id, t_dives.dive_site -> t_dive_site.
WITH ranked AS (SELECT d.dive_site AS site_id,
                       c.water_type,
                       ROW_NUMBER() OVER (PARTITION BY d.dive_site
                           ORDER BY COUNT(*) DESC, c.water_type) AS rn
                FROM t_dive_conditions c
                         JOIN t_dives d ON d.pk_dive_id = c.fk_dive_id
                WHERE c.water_type IS NOT NULL
                GROUP BY d.dive_site, c.water_type)
UPDATE t_dive_site s
SET water_type = ranked.water_type
FROM ranked
WHERE ranked.rn = 1
  AND s.pk_dive_site_id = ranked.site_id;

-- Water type now lives on the site; remove every existing per-dive override (per the design: the
-- individual overrides are not worth keeping once the site carries the value).
UPDATE t_dive_conditions
SET water_type = NULL
WHERE water_type IS NOT NULL;

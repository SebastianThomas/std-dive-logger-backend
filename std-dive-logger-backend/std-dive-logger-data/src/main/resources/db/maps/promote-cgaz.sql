-- Promotes the ogr2pgsql-loaded geoBoundaries CGAZ staging tables into the live maps schema.
--
-- Executed by MapsImportRunStore#promote in a single transaction once the import Job completed.
-- Statements are separated by a "--;;" line because they are bound as prepared statements, which
-- cannot carry more than one statement each.

DO $$
DECLARE
    staged_countries integer;
    staged_regions integer;
    previous_countries integer;
BEGIN
    SELECT count(*) INTO staged_countries FROM maps_cgaz_stage.adm0;
    SELECT count(*) INTO staged_regions FROM maps_cgaz_stage.adm1;

    SELECT count(*) INTO previous_countries
    FROM maps.admin_boundary
    WHERE source = 'CGAZ' AND admin_level = 2;

    IF staged_countries = 0 OR staged_regions = 0 THEN
        RAISE EXCEPTION 'CGAZ staging result is empty';
    END IF;
    IF previous_countries > 0 AND staged_countries < floor(previous_countries * 0.80) THEN
        RAISE EXCEPTION 'CGAZ country count regressed from % to %',
            previous_countries, staged_countries;
    END IF;
END $$;

--;;

UPDATE maps.site_location SET status = 'STALE';

--;;

-- CGAZ ADM0 rows are identified by their ISO 3166-1 alpha-3 group; shapeName is the country name.
INSERT INTO maps.admin_boundary (
    source, source_shape_id, admin_level, name, iso3166_1_alpha3,
    geometry, source_timestamp, imported_at, fk_import_run_id
)
SELECT 'CGAZ',
       "shapeGroup",
       2,
       "shapeName",
       "shapeGroup",
       ST_Multi(ST_CollectionExtract(ST_MakeValid(geometry), 3)),
       (SELECT source_timestamp FROM maps.import_run WHERE pk_import_run_id = :importRunId),
       now(),
       :importRunId
FROM maps_cgaz_stage.adm0
WHERE "shapeGroup" IS NOT NULL
  AND "shapeName" IS NOT NULL
ON CONFLICT (admin_level, source_shape_id) WHERE source = 'CGAZ' DO UPDATE SET
    name = EXCLUDED.name,
    iso3166_1_alpha3 = EXCLUDED.iso3166_1_alpha3,
    geometry = EXCLUDED.geometry,
    source_timestamp = EXCLUDED.source_timestamp,
    imported_at = EXCLUDED.imported_at,
    fk_import_run_id = EXCLUDED.fk_import_run_id;

--;;

INSERT INTO maps.admin_boundary (
    source, source_shape_id, admin_level, name, iso3166_1_alpha3,
    geometry, source_timestamp, imported_at, fk_import_run_id
)
SELECT 'CGAZ',
       "shapeID",
       4,
       "shapeName",
       "shapeGroup",
       ST_Multi(ST_CollectionExtract(ST_MakeValid(geometry), 3)),
       (SELECT source_timestamp FROM maps.import_run WHERE pk_import_run_id = :importRunId),
       now(),
       :importRunId
FROM maps_cgaz_stage.adm1
WHERE "shapeID" IS NOT NULL
  AND "shapeName" IS NOT NULL
ON CONFLICT (admin_level, source_shape_id) WHERE source = 'CGAZ' DO UPDATE SET
    name = EXCLUDED.name,
    iso3166_1_alpha3 = EXCLUDED.iso3166_1_alpha3,
    geometry = EXCLUDED.geometry,
    source_timestamp = EXCLUDED.source_timestamp,
    imported_at = EXCLUDED.imported_at,
    fk_import_run_id = EXCLUDED.fk_import_run_id;

--;;

DELETE FROM maps.admin_boundary
WHERE source = 'CGAZ'
  AND fk_import_run_id <> :importRunId;

--;;

-- The staging copies are only needed for this promotion and would double the maps disk footprint.
DROP TABLE IF EXISTS maps_cgaz_stage.adm0, maps_cgaz_stage.adm1;

--;;

UPDATE maps.import_run
SET status = 'SUCCEEDED',
    finished_at = now(),
    boundary_count = (SELECT count(*) FROM maps.admin_boundary WHERE source = 'CGAZ'),
    invalid_geometry_count = (
        SELECT count(*) FROM maps.admin_boundary
        WHERE source = 'CGAZ' AND NOT ST_IsValid(geometry)
    )
WHERE pk_import_run_id = :importRunId;

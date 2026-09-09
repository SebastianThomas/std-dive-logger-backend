-- Promotes the osm2pgsql staging tables into the live maps schema.
--
-- Executed by MapsImportRunStore#promote in a single transaction once the import Job completed.
-- Statements are separated by a "--;;" line because they are bound as prepared statements, which
-- cannot carry more than one statement each.

DO $$
DECLARE
    staged_total integer;
    staged_countries integer;
    staged_invalid integer;
    previous_countries integer;
BEGIN
    SELECT count(*),
           count(*) FILTER (WHERE admin_level = 2),
           count(*) FILTER (WHERE NOT ST_IsValid(geometry))
    INTO staged_total, staged_countries, staged_invalid
    FROM maps_osm_stage.admin_boundary;

    SELECT count(*) INTO previous_countries
    FROM maps.admin_boundary
    WHERE admin_level = 2;

    IF staged_total = 0 OR staged_countries = 0 THEN
        RAISE EXCEPTION 'OSM boundary staging result is empty';
    END IF;
    IF staged_invalid > greatest(1, floor(staged_total * 0.01)) THEN
        RAISE EXCEPTION 'Invalid geometry threshold exceeded: % of %', staged_invalid, staged_total;
    END IF;
    IF previous_countries > 0 AND staged_countries < floor(previous_countries * 0.80) THEN
        RAISE EXCEPTION 'Country count regressed from % to %', previous_countries, staged_countries;
    END IF;
END $$;

--;;

UPDATE maps.site_location SET status = 'STALE';

--;;

INSERT INTO maps.admin_boundary (
    source, osm_relation_id, admin_level, name, name_en, iso3166_1, iso3166_2,
    geometry, source_timestamp, imported_at, fk_import_run_id
)
SELECT 'OSM',
       osm_relation_id,
       admin_level,
       name,
       name_en,
       iso3166_1,
       iso3166_2,
       ST_Multi(ST_CollectionExtract(ST_MakeValid(geometry), 3)),
       (SELECT source_timestamp FROM maps.import_run WHERE pk_import_run_id = :importRunId),
       now(),
       :importRunId
FROM maps_osm_stage.admin_boundary
ON CONFLICT (admin_level, osm_relation_id) WHERE source = 'OSM' DO UPDATE SET
    name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    iso3166_1 = EXCLUDED.iso3166_1,
    iso3166_2 = EXCLUDED.iso3166_2,
    geometry = EXCLUDED.geometry,
    source_timestamp = EXCLUDED.source_timestamp,
    imported_at = EXCLUDED.imported_at,
    fk_import_run_id = EXCLUDED.fk_import_run_id;

--;;

DELETE FROM maps.admin_boundary
WHERE source = 'OSM'
  AND fk_import_run_id <> :importRunId;

--;;

UPDATE maps.import_run
SET status = 'SUCCEEDED',
    finished_at = now(),
    boundary_count = (SELECT count(*) FROM maps.admin_boundary WHERE source = 'OSM'),
    invalid_geometry_count = (
        SELECT count(*) FROM maps.admin_boundary
        WHERE source = 'OSM' AND NOT ST_IsValid(geometry)
    )
WHERE pk_import_run_id = :importRunId;

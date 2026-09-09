\set ON_ERROR_STOP on
BEGIN;

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

UPDATE maps.site_location SET status = 'STALE';

INSERT INTO maps.admin_boundary (
    osm_relation_id, admin_level, name, name_en, iso3166_1, iso3166_2,
    geometry, source_timestamp, imported_at, fk_import_run_id
)
SELECT osm_relation_id,
       admin_level,
       name,
       name_en,
       iso3166_1,
       iso3166_2,
       ST_Multi(ST_CollectionExtract(ST_MakeValid(geometry), 3)),
       :'source_timestamp'::timestamptz,
       now(),
       :'import_run_id'::bigint
FROM maps_osm_stage.admin_boundary
ON CONFLICT (admin_level, osm_relation_id) DO UPDATE SET
    name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    iso3166_1 = EXCLUDED.iso3166_1,
    iso3166_2 = EXCLUDED.iso3166_2,
    geometry = EXCLUDED.geometry,
    source_timestamp = EXCLUDED.source_timestamp,
    imported_at = EXCLUDED.imported_at,
    fk_import_run_id = EXCLUDED.fk_import_run_id;

DELETE FROM maps.admin_boundary
WHERE fk_import_run_id <> :'import_run_id'::bigint;

UPDATE maps.import_run
SET status = 'SUCCEEDED',
    finished_at = now(),
    boundary_count = (SELECT count(*) FROM maps.admin_boundary),
    invalid_geometry_count = (
        SELECT count(*) FROM maps.admin_boundary WHERE NOT ST_IsValid(geometry)
    )
WHERE pk_import_run_id = :'import_run_id'::bigint;

COMMIT;

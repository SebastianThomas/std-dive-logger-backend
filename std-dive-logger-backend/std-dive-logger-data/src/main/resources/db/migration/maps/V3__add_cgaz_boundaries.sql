-- geoBoundaries CGAZ (Comprehensive Global Administrative Zones) as a second boundary source.
-- CGAZ covers the whole world, the osm2pgsql import only covers its configured extract, so both
-- sources share admin_boundary and lookups prefer the more precise OSM geometry where it exists.
-- CGAZ ADM0/ADM1 are stored as the OSM-equivalent admin levels 2/4 to keep lookups source-agnostic.

CREATE SCHEMA IF NOT EXISTS maps_cgaz_stage;

ALTER TABLE import_run
    ADD COLUMN kind TEXT NOT NULL DEFAULT 'OSM',
    ADD CONSTRAINT chk_import_run_kind CHECK (kind IN ('OSM', 'CGAZ'));

CREATE INDEX idx_import_run_kind_successful_state
    ON import_run (kind, state, finished_at DESC)
    WHERE status = 'SUCCEEDED';

ALTER TABLE admin_boundary
    ADD COLUMN source TEXT NOT NULL DEFAULT 'OSM',
    -- CGAZ ADM1 has its own shape id; its ADM0 rows are identified by their ISO 3166-1 alpha-3.
    ADD COLUMN source_shape_id TEXT,
    -- CGAZ carries alpha-3 country codes, OSM carries the alpha-2 codes in iso3166_1.
    ADD COLUMN iso3166_1_alpha3 TEXT,
    ALTER COLUMN osm_relation_id DROP NOT NULL,
    ADD CONSTRAINT chk_admin_boundary_source CHECK (source IN ('OSM', 'CGAZ')),
    ADD CONSTRAINT chk_admin_boundary_source_id CHECK (
        (source = 'OSM' AND osm_relation_id IS NOT NULL)
        OR (source = 'CGAZ' AND source_shape_id IS NOT NULL)
    );

ALTER TABLE admin_boundary
    DROP CONSTRAINT uq_admin_boundary_relation;

CREATE UNIQUE INDEX uq_admin_boundary_osm_relation
    ON admin_boundary (admin_level, osm_relation_id)
    WHERE source = 'OSM';
CREATE UNIQUE INDEX uq_admin_boundary_cgaz_shape
    ON admin_boundary (admin_level, source_shape_id)
    WHERE source = 'CGAZ';
CREATE INDEX idx_admin_boundary_source_level
    ON admin_boundary (source, admin_level);

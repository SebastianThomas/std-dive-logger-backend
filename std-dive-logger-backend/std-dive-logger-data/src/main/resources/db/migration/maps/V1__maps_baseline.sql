CREATE SCHEMA IF NOT EXISTS maps_osm_stage;

CREATE TABLE import_run
(
    pk_import_run_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_url             TEXT                     NOT NULL,
    source_timestamp       TIMESTAMP WITH TIME ZONE,
    source_checksum        TEXT,
    kubernetes_job_name    TEXT                     NOT NULL UNIQUE,
    status                 TEXT                     NOT NULL,
    started_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at            TIMESTAMP WITH TIME ZONE,
    boundary_count         INTEGER,
    invalid_geometry_count INTEGER,
    failure_summary        TEXT,
    CONSTRAINT chk_import_run_status CHECK (
        status IN ('PLANNED', 'SUBMITTED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_import_run_counts CHECK (
        (boundary_count IS NULL OR boundary_count >= 0)
        AND (invalid_geometry_count IS NULL OR invalid_geometry_count >= 0)
    )
);

CREATE INDEX idx_import_run_status_started
    ON import_run (status, started_at DESC);

CREATE TABLE admin_boundary
(
    pk_admin_boundary_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    osm_relation_id      BIGINT                   NOT NULL,
    admin_level          SMALLINT                 NOT NULL,
    name                 TEXT                     NOT NULL,
    name_en              TEXT,
    iso3166_1            TEXT,
    iso3166_2            TEXT,
    parent_relation_id   BIGINT,
    geometry             geometry(MultiPolygon, 4326) NOT NULL,
    source_timestamp     TIMESTAMP WITH TIME ZONE,
    imported_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    fk_import_run_id     BIGINT                   NOT NULL
        REFERENCES import_run (pk_import_run_id),
    CONSTRAINT uq_admin_boundary_relation UNIQUE (admin_level, osm_relation_id),
    CONSTRAINT chk_admin_boundary_level CHECK (admin_level IN (2, 4)),
    CONSTRAINT chk_admin_boundary_geometry CHECK (NOT ST_IsEmpty(geometry))
);

CREATE INDEX idx_admin_boundary_geometry
    ON admin_boundary USING GIST (geometry);
CREATE INDEX idx_admin_boundary_level_relation
    ON admin_boundary (admin_level, osm_relation_id);
CREATE INDEX idx_admin_boundary_iso3166_1
    ON admin_boundary (iso3166_1) WHERE iso3166_1 IS NOT NULL;
CREATE INDEX idx_admin_boundary_iso3166_2
    ON admin_boundary (iso3166_2) WHERE iso3166_2 IS NOT NULL;

CREATE TABLE site_location
(
    fk_dive_site_id          INTEGER PRIMARY KEY
        REFERENCES public.t_dive_site (pk_dive_site_id) ON DELETE CASCADE,
    fk_country_boundary_id   BIGINT
        REFERENCES admin_boundary (pk_admin_boundary_id) ON DELETE SET NULL,
    country_name             TEXT,
    country_code             TEXT,
    fk_region_boundary_id    BIGINT
        REFERENCES admin_boundary (pk_admin_boundary_id) ON DELETE SET NULL,
    region_name              TEXT,
    region_code              TEXT,
    status                   TEXT                     NOT NULL,
    resolved_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    boundary_import_version  BIGINT
        REFERENCES import_run (pk_import_run_id),
    CONSTRAINT chk_site_location_status CHECK (
        status IN ('RESOLVED', 'NO_COUNTRY', 'AMBIGUOUS', 'STALE')
    )
);

CREATE INDEX idx_site_location_country_boundary
    ON site_location (fk_country_boundary_id);
CREATE INDEX idx_site_location_region_boundary
    ON site_location (fk_region_boundary_id);
CREATE INDEX idx_site_location_import_version
    ON site_location (boundary_import_version);

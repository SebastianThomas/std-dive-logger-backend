-- Opt-in retention of uploaded dive files, so they can be re-processed whenever an importer
-- improves. The bytes live on a local volume (ch.sthomas.stddivelogger.import-files.dir); these
-- tables record what each file is, which profiles and dive fields came from it, and re-processing
-- results that would change real data and wait for the diver's review.
ALTER TABLE t_users
    ADD COLUMN keep_import_files BOOLEAN NOT NULL DEFAULT FALSE;

-- v_readers selects u.*, which Postgres expands once, at CREATE VIEW time: recreated so full-user
-- reads through it (UserRepository.findReaders) see the new column - the same fix as in V0_4_1.
DROP VIEW v_readers;

CREATE VIEW v_readers AS
-- Diver
SELECT d.pk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_dives d ON u.pk_user_id = d.fk_diver_id
UNION
-- Buddies (may include diver if >= 1 buddy)
SELECT d.pk_dive_id AS dive_id, u.*
FROM t_dive_buddy b
         INNER JOIN t_dives d
                    ON b.fk_dive_id = d.pk_dive_id OR b.fk_buddy_dive_id = d.pk_dive_id
         INNER JOIN t_users u ON d.fk_diver_id = u.pk_user_id
UNION
-- Explicit Readers
SELECT p.fk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_dive_privileges p ON u.pk_user_id = p.fk_user_id
UNION
-- Group Readers
SELECT g.fk_dive_id AS dive_id, u.*
FROM t_users u
         INNER JOIN t_group_member m ON u.pk_user_id = m.fk_user_id AND role IN ('MEMBER', 'ADMIN')
         INNER JOIN t_dive_privileges_groups g ON g.fk_group_id = m.fk_group_id;

-- One row per distinct file of an account: uploading the same bytes again reuses it.
CREATE TABLE t_import_file
(
    pk_import_file_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id        INTEGER     NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    source            TEXT        NOT NULL, -- PendingImportSource
    original_filename TEXT,
    content_type      TEXT        NOT NULL,
    size_bytes        BIGINT      NOT NULL,
    sha256            TEXT        NOT NULL,
    storage_path      TEXT        NOT NULL, -- relative to the import-files directory
    dive_count        INTEGER     NOT NULL,
    profile_count     INTEGER     NOT NULL,
    scope             TEXT        NOT NULL, -- SINGLE_PROFILE | SINGLE_DIVE_MULTI_PROFILE | MULTI_DIVE
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_import_file_user_sha256 UNIQUE (fk_user_id, sha256)
);

-- Which files a profile was built from - several after a refine (e.g. UDDF + native XML).
CREATE TABLE t_dive_profile_import_file
(
    pk_dive_profile_import_file_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_profile_id INTEGER     NOT NULL REFERENCES t_dive_profiles (pk_dive_profile_id) ON DELETE CASCADE,
    fk_import_file_id  BIGINT      NOT NULL REFERENCES t_import_file (pk_import_file_id) ON DELETE CASCADE,
    locator            JSONB       NOT NULL DEFAULT '{}', -- {"entry":3,"profile":0} | {"id":"...","profile":0}
    parser_version     INTEGER     NOT NULL,
    -- First sample deeper than 0.5 m as this file reads it (site timezone applied): tells a clock
    -- change by a newer importer apart from the diver's own alignment.
    raw_active_start   TIMESTAMPTZ NOT NULL,
    processed_site_id  INTEGER, -- the dive site whose timezone raw_active_start used
    last_processed_at  TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dive_profile_import_file UNIQUE (fk_dive_profile_id, fk_import_file_id, locator)
);
CREATE INDEX idx_dive_profile_import_file_file ON t_dive_profile_import_file (fk_import_file_id);

-- Which files contributed dive-level information ...
CREATE TABLE t_dive_import_file
(
    pk_dive_import_file_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id        INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    fk_import_file_id BIGINT      NOT NULL REFERENCES t_import_file (pk_import_file_id) ON DELETE CASCADE,
    locator           JSONB       NOT NULL DEFAULT '{}',
    parser_version    INTEGER     NOT NULL,
    last_processed_at TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dive_import_file UNIQUE (fk_dive_id, fk_import_file_id, locator)
);
CREATE INDEX idx_dive_import_file_file ON t_dive_import_file (fk_import_file_id);

-- ... and which information exactly, one row per field. imported_value tells "still what the file
-- said" apart from "edited by the diver since".
CREATE TABLE t_dive_import_file_field
(
    pk_dive_import_file_field_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_import_file_id BIGINT      NOT NULL REFERENCES t_dive_import_file (pk_dive_import_file_id) ON DELETE CASCADE,
    field                  TEXT        NOT NULL, -- ImportedDiveField
    imported_value         JSONB,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dive_import_file_field UNIQUE (fk_dive_import_file_id, field)
);

-- Re-processing results that would change real data; reviewed on the backfill page.
CREATE TABLE t_import_reprocess_conflict
(
    pk_import_reprocess_conflict_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_user_id             INTEGER     NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    fk_dive_id             INTEGER     NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    fk_dive_profile_id     INTEGER REFERENCES t_dive_profiles (pk_dive_profile_id) ON DELETE CASCADE,
    fk_dive_import_file_id BIGINT REFERENCES t_dive_import_file (pk_dive_import_file_id) ON DELETE CASCADE,
    kind                   TEXT        NOT NULL, -- PROFILE | DIVE_FIELD
    field                  TEXT,                 -- DIVE_FIELD only
    summary                TEXT        NOT NULL,
    current_value          JSONB,
    proposed_value         JSONB       NOT NULL,
    status                 TEXT        NOT NULL, -- OPEN | APPLIED | KEPT_CURRENT | SUPERSEDED
    resolved_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- At most one open conflict per profile / dive field; a newer run supersedes it.
CREATE UNIQUE INDEX uq_import_reprocess_conflict_open
    ON t_import_reprocess_conflict (fk_dive_id, coalesce(fk_dive_profile_id, 0), coalesce(field, ''))
    WHERE status = 'OPEN';
CREATE INDEX idx_import_reprocess_conflict_user ON t_import_reprocess_conflict (fk_user_id, status);

-- Carries the stored file from staging to commit.
ALTER TABLE t_pending_import
    ADD COLUMN fk_import_file_id BIGINT REFERENCES t_import_file (pk_import_file_id) ON DELETE SET NULL,
    ADD COLUMN import_locator    JSONB;

-- What re-processing must keep when it re-derives a profile from its files.
ALTER TABLE t_dive_profile_history
    -- Kept window, relative to the first sample deeper than 0.5 m (clock-independent).
    ADD COLUMN trim_start_offset_ms  BIGINT,
    ADD COLUMN trim_end_offset_ms    BIGINT,
    -- Every sample came from linked files: re-processing may replace the profile, not only add.
    ADD COLUMN import_files_complete BOOLEAN NOT NULL DEFAULT FALSE;

-- Holds a parsed-but-not-yet-persisted dive import: the frontend gets back a cheap summary (this
-- row's "guess" columns) and only ever sends back a small overrides object at commit time, instead
-- of round-tripping the full parsed profile/measurement data.
CREATE TABLE t_pending_import
(
    pk_pending_import_id  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_diver_id           INTEGER                  NOT NULL REFERENCES t_users (pk_user_id) ON DELETE CASCADE,
    source                TEXT                     NOT NULL, -- DIVESOFT | FIT_GARMIN | UDDF_SHEARWATER | XML_SUBSURFACE
    external_id           TEXT,                               -- e.g. divesoft dive.id(); null for file uploads
    filename              TEXT,
    dive_identifier_guess TEXT,
    site_name_guess       TEXT,
    latitude_guess        DOUBLE PRECISION,
    longitude_guess       DOUBLE PRECISION,
    computer_serial       TEXT,
    start_date            TIMESTAMP WITH TIME ZONE,
    duration_seconds      BIGINT,
    max_depth             DOUBLE PRECISION,
    payload               TEXT                     NOT NULL, -- JSON: profiles + everything needed to commit
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_pending_import_user ON t_pending_import (fk_diver_id);
CREATE INDEX idx_pending_import_created_at ON t_pending_import (created_at);

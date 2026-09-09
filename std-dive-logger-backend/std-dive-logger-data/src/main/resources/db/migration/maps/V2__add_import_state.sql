ALTER TABLE import_run
    ADD COLUMN state TEXT;

UPDATE import_run
SET state = 'legacy:' || pk_import_run_id
WHERE state IS NULL;

ALTER TABLE import_run
    ALTER COLUMN state SET NOT NULL;

CREATE INDEX idx_import_run_successful_state
    ON import_run (state, finished_at DESC)
    WHERE status = 'SUCCEEDED';

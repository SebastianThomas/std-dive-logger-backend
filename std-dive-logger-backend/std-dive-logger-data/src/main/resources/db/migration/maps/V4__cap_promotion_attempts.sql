-- Counted (and committed) before each promotion, so an attempt that crashes the database - which
-- rolls back everything inside it - is still counted. See MapsImportRunStore#startPromotionAttempt.
ALTER TABLE import_run
    ADD COLUMN promotion_attempts INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_import_run_promotion_attempts CHECK (promotion_attempts >= 0);

-- In-flight CGAZ runs staged their geometries without ogr2ogr -makevalid, which the promotion now
-- relies on; retire them so only a fresh (re-staged) run gets promoted.
UPDATE import_run
SET status = 'FAILED',
    failure_summary = 'SupersededByMakeValidStaging',
    finished_at = now()
WHERE kind = 'CGAZ'
  AND status IN ('PLANNED', 'SUBMITTED', 'RUNNING');

-- Tracks, per dive and per analytics job, the algorithm version last computed for it and when.
-- Lets a job tell exactly which dives are stale (never computed, or computed at an older
-- version) instead of blindly reprocessing everything or relying on a single global watermark.
CREATE TABLE t_analytics_job_state
(
    pk_analytics_job_state_id INTEGER                  NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fk_dive_id                INTEGER                  NOT NULL REFERENCES t_dives (pk_dive_id) ON DELETE CASCADE,
    module                    TEXT                     NOT NULL,
    job_name                  TEXT                     NOT NULL,
    version                   BIGINT                   NOT NULL,
    computed_at               TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (fk_dive_id, module, job_name)
);

CREATE INDEX idx_analytics_job_state_module_job_version
    ON t_analytics_job_state (module, job_name, version);

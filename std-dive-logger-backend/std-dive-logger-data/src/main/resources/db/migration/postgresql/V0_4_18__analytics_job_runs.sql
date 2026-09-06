CREATE TABLE t_analytics_job_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job VARCHAR(64) NOT NULL,
    trigger VARCHAR(16) NOT NULL CHECK (trigger IN ('SCHEDULED', 'MANUAL')),
    status VARCHAR(16) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'INTERRUPTED')),
    queued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    error_type VARCHAR(256)
);
CREATE UNIQUE INDEX analytics_job_run_active ON t_analytics_job_run(job)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX analytics_job_run_recent ON t_analytics_job_run(queued_at DESC);

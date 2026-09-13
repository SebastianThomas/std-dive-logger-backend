-- Bumped by every change to a dive that the analytics job must not overwrite with a result computed
-- before it: analytics invalidations (attach/reimport/trim/align) and plain dive saves. The job only
-- records a dive as computed while the generation it read the dive at is still the current one.
ALTER TABLE t_dives
    ADD COLUMN analytics_generation BIGINT NOT NULL DEFAULT 0;

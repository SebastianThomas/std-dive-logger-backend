ALTER TABLE t_dive_measurements
    ADD COLUMN time_to_surface_seconds INTEGER;

ALTER TABLE t_dive_summary
    ADD COLUMN max_time_to_surface_seconds INTEGER;

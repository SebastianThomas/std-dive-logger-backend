ALTER TABLE t_dive_measurements ADD COLUMN elapsed interval;
UPDATE t_dive_measurements dm SET elapsed = dm.time - dp.dive_profile_start
FROM t_dive_profiles dp WHERE dp.pk_dive_profile_id = dm.fk_dive_profile_id;
ALTER TABLE t_dive_measurements ALTER COLUMN elapsed SET NOT NULL;
ALTER TABLE t_dive_measurements DROP COLUMN time;

ALTER TABLE t_dive_configuration_cylinder_usage_window
    ADD COLUMN start_offset interval, ADD COLUMN end_offset interval;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM t_dive_configuration_cylinder_usage_window w
        JOIN t_dive_configuration_cylinder c ON c.pk_configuration_cylinder_id = w.fk_configuration_cylinder_id
        LEFT JOIN t_dive_summary s ON s.fk_dive_id = c.fk_dive_id
        WHERE s.dive_start IS NULL AND (w.window_start IS NOT NULL OR w.window_end IS NOT NULL)) THEN
        RAISE EXCEPTION 'Cannot migrate usage windows without a dive start';
    END IF;
END $$;
UPDATE t_dive_configuration_cylinder_usage_window w
SET start_offset = w.window_start - s.dive_start, end_offset = w.window_end - s.dive_start
FROM t_dive_configuration_cylinder cc
JOIN t_dive_summary s ON s.fk_dive_id = cc.fk_dive_id
WHERE cc.pk_configuration_cylinder_id = w.fk_configuration_cylinder_id;
ALTER TABLE t_dive_configuration_cylinder_usage_window DROP COLUMN window_start, DROP COLUMN window_end;

ALTER TABLE t_dive_site ADD COLUMN zone_id VARCHAR(64);

-- A fill recorded from a bank's pressure drop (DOMAIN §5.7) has a charged member but no measured
-- cylinder, so its water volume stays NULL; a cylinder label still needs a cylinder.
ALTER TABLE t_fill DROP CONSTRAINT ck_fill_target;
ALTER TABLE t_fill ADD CONSTRAINT ck_fill_target CHECK (
    (fk_target_vessel_id IS NULL
        AND fk_charged_member_id IS NOT NULL
        AND (water_volume_litres IS NOT NULL OR cylinder_label IS NULL))
    OR (fk_target_vessel_id IS NOT NULL
        AND water_volume_litres IS NULL AND cylinder_label IS NULL AND fk_charged_member_id IS NULL));

-- How each profile's decompression / CNS / OTU figures were computed (algorithm, whose
-- implementation, gradient factors, conservatism, surface pressure, water density, the device's
-- own CNS/OTU/tissue loading, firmware, plus every other raw setting the source file carries).
-- See DecoSettings. Kept per profile so a dive's computers and algorithms can be compared.
ALTER TABLE t_dive_profiles
    ADD COLUMN deco_settings JSONB;

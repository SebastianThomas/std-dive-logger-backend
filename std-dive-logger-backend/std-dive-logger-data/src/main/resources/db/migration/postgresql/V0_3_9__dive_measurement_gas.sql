-- Backend-computed PO2/FO2 per measurement, stored separately from
-- t_dive_measurement_po2.calculated (which is the *source device's own* onboard CCR calculation,
-- populated straight from an import file and never touched here). This table instead holds our
-- own best-estimate value, derived by AnalyticsService from measured PO2 / setpoint / bailout gas
-- across every profile of the dive, and is recomputed the same way segments/rates are.
CREATE TABLE t_dive_measurement_gas
(
    fk_dive_measurement_id INTEGER          NOT NULL REFERENCES t_dive_measurements (pk_dive_measurement_id) ON DELETE CASCADE PRIMARY KEY,
    calculated_po2         DOUBLE PRECISION NOT NULL,
    calculated_fo2         DOUBLE PRECISION NOT NULL
);

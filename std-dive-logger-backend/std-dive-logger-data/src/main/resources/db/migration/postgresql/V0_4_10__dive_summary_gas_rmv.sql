-- Cylinder-derived RMV, persisted per dive so Stats / Trends can aggregate it without
-- re-running CylinderConsumptionCalculator (its windowing / complement-interval / union-denominator
-- math has no faithful SQL form). Exactly one of the two is non-null on any given dive: the
-- calculator produces oc_rmv_liters for an open-circuit dive and bailout_rmv_liters for a dive with
-- closed-circuit samples (bailout gas breathed only over the open-circuit portion). Both null when
-- the dive tracks no usable cylinder.
--
-- Staleness is handled two ways: DiveSummaryEntity#update recomputes these on every dive save (data
-- changes), and gas_computation_version lets the nightly summary job re-derive every row whose
-- stored version is behind DiveSummaryEntity.GAS_COMPUTATION_VERSION (algorithm changes). Default 0
-- means every existing row is behind the current version and gets swept on the next job run.
ALTER TABLE t_dive_summary
    ADD COLUMN oc_rmv_liters           DOUBLE PRECISION,
    ADD COLUMN bailout_rmv_liters      DOUBLE PRECISION,
    ADD COLUMN gas_computation_version SMALLINT NOT NULL DEFAULT 0;

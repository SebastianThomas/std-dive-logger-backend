package ch.sthomas.stddivelogger.model.analytics;

import java.time.Instant;
import java.util.List;

/**
 * The backend's own best-estimate PO2/FO2 at every measurement of a profile - distinct from
 * whatever a source device itself reported (see {@code t_dive_measurement_po2.calculated}), and
 * computed the same way for every profile of a dive together so a bailout on one profile is
 * reflected in the others too. See {@code DivePo2CalculationService}.
 */
public record DiveProfileGasResponse(long profileId, List<GasPoint> points) {
    public record GasPoint(Instant time, double po2, double fo2) {}
}

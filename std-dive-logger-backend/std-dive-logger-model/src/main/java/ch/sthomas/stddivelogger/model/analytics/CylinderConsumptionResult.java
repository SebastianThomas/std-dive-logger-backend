package ch.sthomas.stddivelogger.model.analytics;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;
import ch.sthomas.stddivelogger.model.dive.stats.CylinderContribution;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Gas-consumption figures computed from a dive's tracked {@code DiveConfigurationCylinder}s -
 * deliberately not a single "RMV" number, since that concept doesn't apply uniformly across OC and
 * CCR dives (see {@link CylinderConsumptionCalculator}'s own doc comment). Every field is {@code
 * null}, not zero, when there was nothing to compute it from (e.g. no OC-role cylinders tracked on
 * this dive) - a dive with no data for a figure must not silently read as "0", the same reasoning
 * behind the {@code StatsDataService} timeseries RMV fix this figure is meant to feed cleanly into.
 */
public record CylinderConsumptionResult(
        @Nullable Double ocRmvLiters,
        @Nullable Double bailoutRmvLiters,
        @Nullable Double o2Liters,
        @Nullable Double diluentLiters,
        // Total litres consumed across this dive's OC-role cylinders (surface volume), independent
        // of the pressure-minutes weighting behind ocRmvLiters - null when no OC cylinder had
        // usable start/end pressure. Feeds the inserted-vs-calculated gas-consumption check.
        @Nullable Double ocConsumedLiters,
        // The denominator behind ocRmvLiters: pressure-minutes (Sum of segment minutes x avg
        // ambient ATA) over the stretches the OC cylinders were actually breathed. Surfaced so the
        // gas-consistency breakdown can show "RMV = Sum litres / this". Null when ocRmvLiters is.
        @Nullable Double ocPressureMinutes,
        // The denominator behind bailoutRmvLiters: pressure-minutes over the open-circuit (mode ==
        // OC) portion of a CCR dive only. Null when bailoutRmvLiters is.
        @Nullable Double bailoutPressureMinutes,
        // The open-circuit stretches of a CCR dive (mode == OC), for the CCR breakdown's context
        // line. Empty on an OC dive / when the profile carries no per-sample mode.
        List<CylinderUsageWindow> openCircuitWindows,
        // Per-cylinder "show the working" lines (litres, and per-cylinder RMV for breathed
        // cylinders) - see CylinderContribution. Empty when there are no cylinders / no profile.
        List<CylinderContribution> contributions) {
    public static final CylinderConsumptionResult EMPTY =
            new CylinderConsumptionResult(
                    null, null, null, null, null, null, null, List.of(), List.of());
}

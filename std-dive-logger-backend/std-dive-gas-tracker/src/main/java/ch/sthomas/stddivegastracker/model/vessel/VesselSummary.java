package ch.sthomas.stddivegastracker.model.vessel;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.allocation.VesselStatus;
import ch.sthomas.stddivegastracker.calc.gas.GasClass;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API.md §3 {@code VesselSummary}. {@code rate} is per litre at 4 decimals, {@code rateIsEstimate}
 * is the engine's {@code provisional} (DOMAIN §7.1); litres and bar are unrounded.
 */
public record VesselSummary(
        long vesselId,
        String label,
        GasClass gasClass,
        GasMix mix,
        double waterVolumeLitres,
        VesselStatus status,
        LossPolicy lossPolicy,
        double estimatedPressureBar,
        double estimatedContentLitres,
        double usableContentLitres,
        BigDecimal rate,
        boolean rateIsEstimate,
        Instant openedAt,
        @Nullable Instant closedAt) {}

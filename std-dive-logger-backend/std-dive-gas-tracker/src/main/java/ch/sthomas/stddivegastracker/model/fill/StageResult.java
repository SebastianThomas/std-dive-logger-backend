package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * API.md §4 {@code StageResult} (DOMAIN §5.4). {@code charge} is rounded HALF_EVEN to 2 decimals
 * and "0.00" for AIR; {@code isEstimate}: the source bank's price can still change (DOMAIN §7.1);
 * {@code sourcePressureAfterBar}: the bank gauge entered with the stage (an edit sends it back).
 */
public record StageResult(
        int index,
        StageSource source,
        GasMix sourceMix,
        double pressureAfterBar,
        double addedLitres,
        double addedO2Litres,
        double addedHeLitres,
        double lineLossLitres,
        double drawLitres,
        BigDecimal charge,
        boolean isEstimate,
        @Nullable Double sourcePressureAfterBar) {}

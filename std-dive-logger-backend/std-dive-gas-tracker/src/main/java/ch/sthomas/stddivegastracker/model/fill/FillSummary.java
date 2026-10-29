package ch.sthomas.stddivegastracker.model.fill;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.team.MemberRef;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API.md §4 {@code FillSummary}. {@code heLitres}/{@code o2Litres}: Σ gas added from banks (not the
 * O2 in air); {@code targetLabel}: the cylinder label, else "12 L cylinder", else the bank's label.
 */
public record FillSummary(
        long fillId,
        Instant filledAt,
        @Nullable MemberRef chargedMember,
        String targetLabel,
        double endPressureBar,
        GasMix endMix,
        double heLitres,
        double o2Litres,
        BigDecimal totalCharge,
        FillStatus status) {}

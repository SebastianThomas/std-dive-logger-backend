package ch.sthomas.stddivegastracker.model.report;

import ch.sthomas.stddivegastracker.model.team.MemberRef;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * API.md §6 consumption: per charged member, their non-voided cylinder fills in the period, the
 * bought O2/He they took (vessel stages only, not the O2 in air) and what it costs now.
 */
public record ConsumptionReport(List<Row> rows) {

    public ConsumptionReport {
        rows = List.copyOf(rows);
    }

    @Schema(name = "ConsumptionRow")
    public record Row(
            MemberRef member, double o2Litres, double heLitres, long fills, BigDecimal charge) {}
}

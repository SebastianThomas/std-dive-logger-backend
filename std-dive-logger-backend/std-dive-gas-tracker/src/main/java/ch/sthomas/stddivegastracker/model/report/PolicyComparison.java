package ch.sthomas.stddivegastracker.model.report;

import ch.sthomas.stddivegastracker.model.team.MemberRef;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * API.md §6 / DOMAIN §7.6: each member's balance with every bank priced TRUE_UP and with every bank
 * POOLED; {@code delta = pooled − trueUp} (positive: the pool is better for that member).
 */
public record PolicyComparison(List<Row> members) {

    public PolicyComparison {
        members = List.copyOf(members);
    }

    @Schema(name = "PolicyComparisonRow")
    public record Row(MemberRef member, BigDecimal trueUp, BigDecimal pooled, BigDecimal delta) {}
}

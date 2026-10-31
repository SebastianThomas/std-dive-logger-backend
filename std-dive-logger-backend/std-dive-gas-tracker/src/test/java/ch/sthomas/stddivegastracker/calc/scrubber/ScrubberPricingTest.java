package ch.sthomas.stddivegastracker.calc.scrubber;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.scrubber.ScrubberPricing.Canister;
import ch.sthomas.stddivegastracker.calc.scrubber.ScrubberPricing.CostLine;
import ch.sthomas.stddivegastracker.calc.scrubber.ScrubberPricing.Use;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

final class ScrubberPricingTest {

    private static final List<CostLine> PAID_230 = List.of(new CostLine(1, new BigDecimal("230")));

    @Test
    void anOpenCanisterPricesAtItsCapacityAsAnEstimate() {
        final var priced =
                ScrubberPricing.price(
                        new Canister(1, 23, false, PAID_230, List.of(new Use(10, 2, 2.5))));

        assertThat(priced.pricePerKg()).isEqualByComparingTo("10");
        assertThat(priced.isEstimate()).isTrue();
        assertThat(priced.chargeByUseId().get(10L)).isEqualByComparingTo("25");
        assertThat(priced.unallocated()).isEqualByComparingTo("205");
    }

    @Test
    void anEmptyCanisterSharesItsWholeCostByWhatWasRecorded() {
        final var priced =
                ScrubberPricing.price(
                        new Canister(
                                1,
                                23,
                                true,
                                PAID_230,
                                List.of(new Use(10, 2, 15), new Use(11, 3, 5))));

        assertThat(priced.pricePerKg()).isEqualByComparingTo("11.5");
        assertThat(priced.isEstimate()).isFalse();
        assertThat(priced.chargeByUseId().get(10L)).isEqualByComparingTo("172.5");
        assertThat(priced.chargeByUseId().get(11L)).isEqualByComparingTo("57.5");
        assertThat(priced.unallocated()).isEqualByComparingTo("0");
    }

    @Test
    void moreRecordedThanItsCapacityNeverChargesMoreThanItCost() {
        final var priced =
                ScrubberPricing.price(
                        new Canister(1, 23, false, PAID_230, List.of(new Use(10, 2, 46))));

        assertThat(priced.pricePerKg()).isEqualByComparingTo("5");
        assertThat(priced.chargeByUseId().get(10L)).isEqualByComparingTo("230");
        assertThat(priced.unallocated()).isEqualByComparingTo("0");
    }

    @Test
    void anEmptyCanisterNobodyRecordedChargesNobodyAndStaysWithItsPayers() {
        final var priced = ScrubberPricing.price(new Canister(1, 23, true, PAID_230, List.of()));

        assertThat(priced.pricePerKg()).isEqualByComparingTo("0");
        assertThat(priced.unallocated()).isEqualByComparingTo("230");
    }

    @Test
    void aCanisterWithoutCostChargesNothing() {
        final var priced =
                ScrubberPricing.price(
                        new Canister(1, 23, false, List.of(), List.of(new Use(10, 2, 2.5))));

        assertThat(priced.chargeByUseId().get(10L)).isEqualByComparingTo("0");
    }

    @Test
    void memberTotalsAddUpAcrossCanistersAndMarkWhatIsStillAnEstimate() {
        final var totals =
                ScrubberPricing.memberTotals(
                        List.of(
                                new Canister(1, 23, false, PAID_230, List.of(new Use(10, 2, 2.3))),
                                new Canister(
                                        2,
                                        23,
                                        true,
                                        List.of(new CostLine(2, new BigDecimal("100"))),
                                        List.of(new Use(11, 2, 10)))));

        assertThat(totals).hasSize(2);
        final var payer = totals.get(0);
        assertThat(payer.memberId()).isEqualTo(1);
        assertThat(payer.paid()).isEqualByComparingTo("230");
        assertThat(payer.charged()).isEqualByComparingTo("0");
        final var diver = totals.get(1);
        assertThat(diver.memberId()).isEqualTo(2);
        assertThat(diver.paid()).isEqualByComparingTo("100");
        assertThat(diver.charged()).isEqualByComparingTo("123");
        assertThat(diver.provisionalCharged()).isEqualByComparingTo("23");
    }
}

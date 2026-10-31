package ch.sthomas.stddivegastracker.calc.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.MemberBalance;
import ch.sthomas.stddivegastracker.calc.settlement.SettlementSuggester.Transfer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Edge cases and properties of {@link SettlementSuggester}; goldens are in SettlementGoldenTest.
 */
final class SettlementSuggesterTest {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private static MemberBalance member(final long memberId, final String balance) {
        return member(memberId, new BigDecimal(balance));
    }

    private static MemberBalance member(final long memberId, final BigDecimal balance) {
        return new MemberBalance(
                memberId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                balance,
                BigDecimal.ZERO);
    }

    private static Transfer transfer(final long from, final long to, final String amount) {
        return new Transfer(from, to, new BigDecimal(amount));
    }

    @Test
    void nothingToSettleGivesNoTransfers() {
        assertThat(SettlementSuggester.suggest(List.of())).isEmpty();
        assertThat(SettlementSuggester.suggest(List.of(member(1, "0"), member(2, "0.00"))))
                .isEmpty();
        // A lone creditor has nobody to collect from.
        assertThat(SettlementSuggester.suggest(List.of(member(1, "12.34")))).isEmpty();
    }

    @Test
    void singleCreditorKeepsTheInventoryBackedRemainder() {
        final var transfers =
                SettlementSuggester.suggest(
                        List.of(member(1, "540.00"), member(2, "-180.00"), member(3, "-120.00")));

        assertThat(transfers).containsExactly(transfer(2, 1, "180.00"), transfer(3, 1, "120.00"));
        // 240.00 of the 540.00 stays outstanding (backed by gas in stock, DOMAIN §8).
        assertThat(sum(transfers)).isEqualByComparingTo("300.00");
    }

    @Test
    void largestDebtorPaysLargestCreditorAndPartialPaymentsContinue() {
        final var transfers =
                SettlementSuggester.suggest(
                        List.of(
                                member(1, "300.00"),
                                member(2, "100.00"),
                                member(3, "-250.00"),
                                member(4, "-150.00")));

        // 3 (-250) pays 1 (300) -> 1 has 50 left; 4 (-150) pays 2 (100), then 1 (50).
        assertThat(transfers)
                .containsExactly(
                        transfer(3, 1, "250.00"),
                        transfer(4, 2, "100.00"),
                        transfer(4, 1, "50.00"));
    }

    @Test
    void tiesGoToTheLowerMemberIdWhateverTheInputOrder() {
        final var transfers =
                SettlementSuggester.suggest(
                        List.of(
                                member(7, "-100.00"),
                                member(3, "-100.00"),
                                member(5, "100.00"),
                                member(2, "100.00")));

        // Debtors 3 and 7 tie, creditors 2 and 5 tie: 3 pays 2 first, then 7 pays 5.
        assertThat(transfers).containsExactly(transfer(3, 2, "100.00"), transfer(7, 5, "100.00"));
    }

    @Test
    void balancesAreRoundedHalfEvenToTwoDecimalsFirst() {
        final var transfers =
                SettlementSuggester.suggest(
                        List.of(
                                member(1, "0.035"), // -> 0.04
                                member(2, "-0.025"), // -> -0.02 (HALF_UP would give -0.03)
                                member(3, "-0.005"), // -> 0.00
                                member(4, "-0.015"))); // -> -0.02

        assertThat(transfers).containsExactly(transfer(2, 1, "0.02"), transfer(4, 1, "0.02"));
    }

    @Test
    void subCentResiduesCreateNoTransfers() {
        assertThat(
                        SettlementSuggester.suggest(
                                List.of(
                                        member(1, "0.004"),
                                        member(2, "-0.004"),
                                        member(3, "-0.0049"))))
                .isEmpty();

        final var transfers =
                SettlementSuggester.suggest(
                        List.of(
                                member(1, "100.004"),
                                member(2, "-50.004"),
                                member(3, "-50.004"),
                                member(4, "-0.004")));
        assertThat(transfers).containsExactly(transfer(2, 1, "50.00"), transfer(3, 1, "50.00"));
    }

    /** Rounding can leave more debt than credit; the suggester stops instead of inventing one. */
    @Test
    void debtBeyondTheCreditorsIsLeftOpen() {
        final var transfers =
                SettlementSuggester.suggest(
                        List.of(member(1, "0.014"), member(2, "-0.006"), member(3, "-0.006")));

        // Rounded: 1 = +0.01, 2 = -0.01, 3 = -0.01 -> only one cent can move.
        assertThat(transfers).containsExactly(transfer(2, 1, "0.01"));
    }

    @Test
    void randomBalancesAreSettledDeterministically() {
        for (final int tieHeavy : new int[] {0, 1}) {
            final long seed = 20_271_026L + tieHeavy;
            final var rnd = new Random(seed);
            for (int i = 0; i < 2000; i++) {
                final var members = randomMembers(rnd, tieHeavy == 1);
                try {
                    checkSuggestions(members, rnd);
                } catch (final AssertionError | RuntimeException e) {
                    throw new AssertionError(
                            "tieHeavy="
                                    + tieHeavy
                                    + ", seed="
                                    + seed
                                    + ", round="
                                    + i
                                    + ": "
                                    + members,
                            e);
                }
            }
        }
    }

    private static List<MemberBalance> randomMembers(final Random rnd, final boolean tieHeavy) {
        final var ids = new ArrayList<Long>();
        for (long id = 1; id <= 20; id++) {
            ids.add(id);
        }
        Collections.shuffle(ids, rnd);
        final var members = new ArrayList<MemberBalance>();
        for (int n = rnd.nextInt(9); n > 0; n--) {
            final var balance =
                    tieHeavy
                            ? BigDecimal.valueOf(50L * (rnd.nextInt(9) - 4)) // many equal values
                            : BigDecimal.valueOf(rnd.nextInt(2_000_001) - 1_000_000, 4);
            members.add(member(ids.get(n), balance));
        }
        return members;
    }

    private static void checkSuggestions(final List<MemberBalance> members, final Random rnd) {
        final var transfers = SettlementSuggester.suggest(members);

        // Input order is irrelevant (ties are broken by member id).
        final var shuffled = new ArrayList<MemberBalance>(members);
        Collections.shuffle(shuffled, rnd);
        assertThat(SettlementSuggester.suggest(shuffled)).isEqualTo(transfers);

        final var rounded = new HashMap<Long, BigDecimal>();
        final var exact = new HashMap<Long, BigDecimal>();
        var roundedTotal = BigDecimal.ZERO;
        for (final var m : members) {
            final var r = m.balance().setScale(2, RoundingMode.HALF_EVEN);
            rounded.put(m.memberId(), r);
            exact.put(m.memberId(), m.balance());
            roundedTotal = roundedTotal.add(r);
        }
        assertThat(transfers.size()).isLessThanOrEqualTo(Math.max(0, members.size() - 1));

        for (final var t : transfers) {
            assertThat(t.fromMemberId()).isNotEqualTo(t.toMemberId());
            assertThat(t.amount()).isGreaterThanOrEqualTo(CENT);
            assertThat(t.amount().scale()).isEqualTo(2);
            rounded.merge(t.fromMemberId(), t.amount(), BigDecimal::add);
            rounded.merge(t.toMemberId(), t.amount().negate(), BigDecimal::add);
            exact.merge(t.fromMemberId(), t.amount(), BigDecimal::add);
            exact.merge(t.toMemberId(), t.amount().negate(), BigDecimal::add);
        }

        // Debtors only pay up to their debt, creditors only receive up to their credit.
        for (final var m : members) {
            final var before = m.balance().setScale(2, RoundingMode.HALF_EVEN);
            final var after = Objects.requireNonNull(rounded.get(m.memberId()));
            if (before.signum() < 0) {
                assertThat(after.signum()).as("debtor %d", m.memberId()).isLessThanOrEqualTo(0);
            } else {
                assertThat(after.signum())
                        .as("creditor %d", m.memberId())
                        .isGreaterThanOrEqualTo(0);
            }
        }

        // Done means: no debt >= 0.01 is left, or (rounding shortfall) no creditor to pay.
        final boolean debtLeft = rounded.values().stream().anyMatch(v -> v.signum() < 0);
        final boolean creditLeft = rounded.values().stream().anyMatch(v -> v.signum() > 0);
        assertThat(debtLeft && creditLeft).as("both debt and credit left").isFalse();
        if (roundedTotal.signum() >= 0) {
            // All debts settled; the unrounded rest is only rounding error (< 0.01).
            assertThat(debtLeft).isFalse();
            exact.forEach(
                    (id, v) -> assertThat(v).as("member %d", id).isGreaterThan(CENT.negate()));
        }
    }

    private static BigDecimal sum(final List<Transfer> transfers) {
        return transfers.stream().map(Transfer::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

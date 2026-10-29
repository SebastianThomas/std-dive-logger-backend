package ch.sthomas.stddivegastracker.calc.settlement;

import ch.sthomas.stddivegastracker.calc.settlement.BalanceCalculator.MemberBalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Settlement suggestions (DOMAIN §8): on balances rounded {@code HALF_EVEN} to 2 dp, the largest
 * debtor repeatedly pays the largest creditor; ties go to the lower member id.
 */
public final class SettlementSuggester {

    private SettlementSuggester() {}

    /** {@code amount} is a positive multiple of 0.01 (2 dp). */
    public record Transfer(long fromMemberId, long toMemberId, BigDecimal amount) {}

    /**
     * Each step settles the debtor or the creditor completely, so at most n - 1 transfers. Stops
     * when no debt (>= 0.01) or no creditor is left; creditors keep what is not claimed (inventory
     * backed, §8).
     */
    public static List<Transfer> suggest(final List<MemberBalance> balances) {
        Objects.requireNonNull(balances, "balances");
        final var members =
                balances.stream()
                        .sorted(Comparator.comparingLong(MemberBalance::memberId))
                        .toList();
        final var open = new BigDecimal[members.size()];
        for (int i = 0; i < open.length; i++) {
            open[i] = members.get(i).balance().setScale(2, RoundingMode.HALF_EVEN);
        }

        final var transfers = new ArrayList<Transfer>();
        while (true) {
            // Ascending id with strict comparisons: the lowest id wins a tie.
            int debtor = -1;
            int creditor = -1;
            for (int i = 0; i < open.length; i++) {
                if (open[i].signum() < 0 && (debtor < 0 || open[i].compareTo(open[debtor]) < 0)) {
                    debtor = i;
                }
                if (open[i].signum() > 0
                        && (creditor < 0 || open[i].compareTo(open[creditor]) > 0)) {
                    creditor = i;
                }
            }
            if (debtor < 0 || creditor < 0) {
                return transfers;
            }
            final var amount = open[creditor].min(open[debtor].negate());
            transfers.add(
                    new Transfer(
                            members.get(debtor).memberId(),
                            members.get(creditor).memberId(),
                            amount));
            open[debtor] = open[debtor].add(amount);
            open[creditor] = open[creditor].subtract(amount);
        }
    }
}

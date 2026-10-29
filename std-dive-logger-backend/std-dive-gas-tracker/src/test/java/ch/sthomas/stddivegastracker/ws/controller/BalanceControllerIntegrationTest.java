package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.service.BalanceService;
import ch.sthomas.stddivegastracker.service.PaymentService;
import ch.sthomas.stddivegastracker.support.AbstractPaymentIntegrationTest;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** API.md §5 {@code GET …/balances}: the numbers (DOMAIN §7.5 #3, §8, §11) and who is listed. */
final class BalanceControllerIntegrationTest extends AbstractPaymentIntegrationTest {

    /** Rounding tolerance of Σ balances per member (each row is rounded on its own). */
    private static final BigDecimal CENT = new BigDecimal("0.01");

    @Autowired BalanceService balanceService;
    @Autowired PaymentService paymentService;
    @Autowired EntityManagerFactory entityManagerFactory;

    // --- the balance identity -----------------------------------------------------------

    @Test
    void whileTheBankIsOpenTrueUpTheBalancesAddUpToTheInventoryAndTheChargesAreEstimates() {
        final var crew = crew();
        final var a = crew.a();
        final var b = crew.b().memberId();
        final var c = crew.c().memberId();
        final var fillA = fillFor(crew, a, 70);
        final var fillB1 = fillFor(crew, b, 92);
        final var fillB2 = fillFor(crew, b, 60);
        final var fillC = fillFor(crew, c, 50);

        final var balances = balancesOf(crew.adminEmail(), crew.teamId());

        // DOMAIN §7.3 open TRUE_UP: rate = 600 / max(V_in - V_res, D), no loss shares yet
        final var drawn = drawnBy(fillA, fillB1, fillB2, fillC);
        final var rate =
                BANK_COST.divide(oxygenInflow().subtract(oxygenResidual(), MC).max(drawn), MC);
        final var chargeA = rate.multiply(drawnBy(fillA), MC);
        final var chargeB = rate.multiply(drawnBy(fillB1, fillB2), MC);
        final var chargeC = rate.multiply(drawnBy(fillC), MC);
        assertThat(balances.currency()).isEqualTo("CHF");
        assertThat(balances.members())
                .extracting(row -> row.member())
                .containsExactlyInAnyOrderElementsOf(refs(crew.adminEmail(), crew.teamId()));

        // A: paid - own charge
        final var rowA = balanceOf(balances, a);
        assertThat(rowA.paidCosts().toPlainString()).isEqualTo("600.00");
        assertThat(rowA.fillCharges().toPlainString()).isEqualTo(money(chargeA));
        assertThat(rowA.lossShares().toPlainString()).isEqualTo("0.00");
        assertThat(rowA.paymentsSent().toPlainString()).isEqualTo("0.00");
        assertThat(rowA.paymentsReceived().toPlainString()).isEqualTo("0.00");
        assertThat(rowA.balance().toPlainString()).isEqualTo(money(BANK_COST.subtract(chargeA)));
        // every charge is still an estimate while the bank is open (§7.1)
        assertThat(rowA.provisionalCharges().toPlainString()).isEqualTo(money(chargeA));
        assertOwes(balances, b, chargeB);
        assertOwes(balances, c, chargeC);

        // §7.5 #3: Σ balances = what the open bank still holds (within a cent per member)
        final var inventory = BANK_COST.subtract(rate.multiply(drawn, MC), MC);
        assertThat(balances.inventoryValue().toPlainString()).isEqualTo(money(inventory));
        assertThat(sumOfBalances(balances).subtract(balances.inventoryValue()).abs())
                .isLessThanOrEqualTo(CENT.multiply(BigDecimal.valueOf(3)));
        // §8: the larger debtor pays the creditor first; the creditor keeps the stock-backed rest
        assertThat(balances.suggestions())
                .extracting(
                        suggestion -> suggestion.from().memberId(),
                        suggestion -> suggestion.to().memberId(),
                        suggestion -> suggestion.amount().toPlainString())
                .containsExactly(tuple(b, a, money(chargeB)), tuple(c, a, money(chargeC)));
    }

    @Test
    void closingTheBankFixesThePricesSoTheBalancesNetToZeroAndTheSuggestionsSettleEveryDebt() {
        final var crew = crew();
        final var a = crew.a();
        final var b = crew.b().memberId();
        final var c = crew.c().memberId();
        final var fillA = fillFor(crew, a, 70);
        final var fillB = fillFor(crew, b, 92);
        final var fillC = fillFor(crew, c, 50);
        close(crew);

        final var balances = balancesOf(crew.adminEmail(), crew.teamId());

        // DOMAIN §7.3 closed TRUE_UP: rate = C / D, the draws pay all of the 600.00
        final var rate = BANK_COST.divide(drawnBy(fillA, fillB, fillC), MC);
        final var chargeA = rate.multiply(drawnBy(fillA), MC);
        final var chargeB = rate.multiply(drawnBy(fillB), MC);
        final var chargeC = rate.multiply(drawnBy(fillC), MC);
        assertThat(balanceOf(balances, a).balance().toPlainString())
                .isEqualTo(money(BANK_COST.subtract(chargeA)));
        assertOwes(balances, b, chargeB);
        assertOwes(balances, c, chargeC);
        for (final var row : balances.members()) {
            assertThat(row.provisionalCharges().toPlainString()).isEqualTo("0.00");
            assertThat(row.lossShares().toPlainString()).isEqualTo("0.00");
        }
        assertThat(balances.inventoryValue().toPlainString()).isEqualTo("0.00");
        assertThat(sumOfBalances(balances).abs())
                .isLessThanOrEqualTo(CENT.multiply(BigDecimal.valueOf(3)));

        // paying every suggestion leaves nobody owing: at most a stray cent of rounding
        assertThat(balances.suggestions())
                .extracting(suggestion -> suggestion.to().memberId())
                .containsExactly(a, a);
        for (final var suggestion : balances.suggestions()) {
            recordPayment(
                    crew.adminEmail(),
                    crew.teamId(),
                    paymentBody(
                            suggestion.from().memberId(),
                            suggestion.to().memberId(),
                            suggestion.amount().toPlainString()));
        }
        final var settled = balancesOf(crew.adminEmail(), crew.teamId());
        assertThat(settled.suggestions()).isEmpty();
        for (final var row : settled.members()) {
            assertThat(row.balance().abs()).isLessThanOrEqualTo(CENT);
        }
        assertThat(balanceOf(settled, b).paymentsSent().toPlainString()).isEqualTo(money(chargeB));
    }

    @Test
    void aPooledBankClosedWithLossSharesItEquallyAmongEveryMemberWhoHasNotLeft() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var b = addActiveMember(admin, team.teamId());
        final var pending =
                addMember(admin, team.teamId(), uniqueEmail("pending"), Role.MEMBER).memberId();
        final var leaver = addActiveMember(admin, team.teamId());
        leave(leaver, team.teamId());
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "POOLED");
        final var fill =
                createFill(
                        admin,
                        team.teamId(),
                        at(
                                fillBody(
                                        cylinder(12, b.memberId()),
                                        0,
                                        fromBank(bank.vesselId(), 92)),
                                day(1)));
        send(
                        HttpMethod.POST,
                        vesselsUri(team.teamId()) + "/" + bank.vesselId() + "/close",
                        admin,
                        Map.of("closingPressureBar", 25))
                .expectStatus()
                .isOk();

        final var balances = balancesOf(admin, team.teamId());

        // DOMAIN §7.3 POOLED: rate = C / V_in, loss = C - rate * D, split equally among the
        // members who have not left - A, B and the PENDING one
        final var charge = BANK_COST.divide(oxygenInflow(), MC).multiply(drawnBy(fill), MC);
        final var share = BANK_COST.subtract(charge, MC).divide(BigDecimal.valueOf(3), MC);
        for (final var memberId : List.of(team.memberId(), b.memberId(), pending)) {
            assertThat(balanceOf(balances, memberId).lossShares().toPlainString())
                    .isEqualTo(money(share));
        }
        assertThat(isListed(balances, leaver.memberId())).isFalse();
        assertThat(balances.members()).hasSize(3);
        assertThat(balanceOf(balances, team.memberId()).balance().toPlainString())
                .isEqualTo(money(BANK_COST.subtract(share, MC)));
        assertThat(balanceOf(balances, b.memberId()).balance().toPlainString())
                .isEqualTo(money(charge.negate().subtract(share, MC)));
        assertThat(balanceOf(balances, pending).balance().toPlainString())
                .isEqualTo(money(share.negate()));
        // closed and drawn from: nothing is left in stock, Σ balances = 0
        assertThat(balances.inventoryValue().toPlainString()).isEqualTo("0.00");
        assertThat(sumOfBalances(balances).abs())
                .isLessThanOrEqualTo(CENT.multiply(BigDecimal.valueOf(3)));
    }

    // --- who is listed ------------------------------------------------------------------

    @Test
    void anIdleTeamListsItsMembersByNameThenIdWithZeroRowsAndHidesTheLeftOnes() {
        final var admin = uniqueEmail("admin");
        final var team =
                createTeam(
                        admin,
                        Map.of(
                                "name",
                                "Divers",
                                "defaultLossPolicy",
                                "TRUE_UP",
                                "currency",
                                "EUR"));
        setDisplayName(admin, "carol");
        final var bob = addActiveMember(admin, team.teamId());
        setDisplayName(emailOf(bob), "Bob");
        final var alice = addActiveMember(admin, team.teamId());
        setDisplayName(emailOf(alice), "alice");
        final var sam1 = addActiveMember(admin, team.teamId());
        setDisplayName(emailOf(sam1), "Sam");
        final var sam2 = addActiveMember(admin, team.teamId());
        setDisplayName(emailOf(sam2), "sam");
        final var pending = addMember(admin, team.teamId(), uniqueEmail("dave"), Role.MEMBER);
        leave(addActiveMember(admin, team.teamId()), team.teamId());

        final var balances = balancesOf(admin, team.teamId());

        assertThat(balances.currency()).isEqualTo("EUR");
        assertThat(balances.inventoryValue().toPlainString()).isEqualTo("0.00");
        assertThat(balances.suggestions()).isEmpty();
        // ACTIVE and PENDING members show with all-zero rows; the LEFT one at 0.00 is hidden
        assertThat(balances.members())
                .extracting(row -> row.member())
                .containsExactly(
                        new MemberRef(alice.memberId(), "alice"),
                        new MemberRef(bob.memberId(), "Bob"),
                        new MemberRef(team.memberId(), "carol"),
                        new MemberRef(pending.memberId(), pending.displayName()),
                        new MemberRef(sam1.memberId(), "Sam"),
                        new MemberRef(sam2.memberId(), "sam"));
        assertThat(pending.displayName()).startsWith("dave");
        for (final var row : balances.members()) {
            assertThat(
                            List.of(
                                    row.paidCosts(),
                                    row.fillCharges(),
                                    row.lossShares(),
                                    row.paymentsSent(),
                                    row.paymentsReceived(),
                                    row.balance(),
                                    row.provisionalCharges()))
                    .extracting(BigDecimal::toPlainString)
                    .containsOnly("0.00");
        }
    }

    @Test
    void aLeftMemberStaysListedWhileTheyOweOrAreOwed() {
        final var crew = crew();
        final var fill = fillFor(crew, crew.c().memberId(), 60);
        leave(crew.c(), crew.teamId());

        final var balances = balancesOf(crew.adminEmail(), crew.teamId());

        final var leaver = balanceOf(balances, crew.c().memberId());
        assertThat(leaver.balance().toPlainString()).startsWith("-");
        assertThat(leaver.fillCharges().toPlainString())
                .isEqualTo(
                        money(
                                BANK_COST
                                        .divide(
                                                oxygenInflow()
                                                        .subtract(oxygenResidual(), MC)
                                                        .max(drawnBy(fill)),
                                                MC)
                                        .multiply(drawnBy(fill), MC)));
        assertThat(balances.suggestions())
                .extracting(suggestion -> suggestion.from().memberId())
                .containsExactly(crew.c().memberId());
    }

    // --- cost ---------------------------------------------------------------------------

    @Test
    void balancesAndPaymentsRunAFixedNumberOfQueriesWhateverTheNumberOfFillsAndPayments() {
        final var crew = crew();
        final var me = me(crew.adminEmail());
        final var user = new GasUser(me.userId(), me.email(), me.displayName());
        final var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            final var idle = balanceService.balances(crew.teamId(), user);
            final var withNothing = statistics.getPrepareStatementCount();

            for (int i = 0; i < 6; i++) {
                fillFor(crew, crew.b().memberId(), 60 + i);
                recordPayment(
                        crew.adminEmail(),
                        crew.teamId(),
                        paymentBody(crew.b().memberId(), crew.a(), "1.00"));
            }
            statistics.clear();
            final var busy = balanceService.balances(crew.teamId(), user);
            final var withSix = statistics.getPrepareStatementCount();
            statistics.clear();
            final var listed = paymentService.list(crew.teamId(), user);
            final var listQueries = statistics.getPrepareStatementCount();

            assertThat(idle.members()).hasSize(3);
            assertThat(busy.members()).hasSize(3);
            assertThat(balanceOf(busy, crew.b().memberId()).paymentsSent().toPlainString())
                    .isEqualTo("6.00");
            // membership, payments, team (currency), then the snapshot's 7 (see D3)
            assertThat(withNothing).isEqualTo(10);
            assertThat(withSix).isEqualTo(10);
            // the payments list: membership, payments, members, their users
            assertThat(listed).hasSize(6);
            assertThat(listQueries).isEqualTo(4);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // --- helpers ------------------------------------------------------------------------

    /** The member owes {@code charge} (rounded), all of it an estimate while the bank is open. */
    private static void assertOwes(
            final Balances balances, final long memberId, final BigDecimal charge) {
        final var row = balanceOf(balances, memberId);
        assertThat(row.paidCosts().toPlainString()).isEqualTo("0.00");
        assertThat(row.fillCharges().toPlainString()).isEqualTo(money(charge));
        assertThat(row.balance().toPlainString()).isEqualTo(money(charge.negate()));
    }

    private List<MemberRef> refs(final String email, final long teamId) {
        return members(email, teamId).stream()
                .map(member -> new MemberRef(member.memberId(), member.displayName()))
                .toList();
    }

    private void leave(final Member member, final long teamId) {
        send(HttpMethod.POST, "/v1/teams/" + teamId + "/members/me/leave", emailOf(member), null)
                .expectStatus()
                .isNoContent();
    }
}

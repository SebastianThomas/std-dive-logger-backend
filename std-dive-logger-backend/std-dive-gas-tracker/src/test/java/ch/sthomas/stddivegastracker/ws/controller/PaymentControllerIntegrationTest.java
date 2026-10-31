package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.payment.Payment;
import ch.sthomas.stddivegastracker.model.team.MemberRef;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.support.AbstractPaymentIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** API.md §5 payments: effect on the balances (DOMAIN §8), the list, the 400s of the body. */
final class PaymentControllerIntegrationTest extends AbstractPaymentIntegrationTest {

    @Test
    void aPaymentMovesBothBalancesByTheAmountLeavesTheSumAndDeletingItRestoresThem() {
        final var crew = crew();
        final var a = crew.a();
        final var b = crew.b().memberId();
        fillFor(crew, b, 92);
        final var before = balancesOf(crew.adminEmail(), crew.teamId());

        // B pays A, recorded by B (the payer)
        final var payment =
                recordPayment(emailOf(crew.b()), crew.teamId(), paymentBody(b, a, "20.50"));

        assertThat(payment.paymentId()).isPositive();
        final var after = balancesOf(crew.adminEmail(), crew.teamId());
        final var paid = new BigDecimal("20.50");
        assertThat(balanceOf(after, b).paymentsSent().toPlainString()).isEqualTo("20.50");
        assertThat(balanceOf(after, b).balance())
                .isEqualByComparingTo(balanceOf(before, b).balance().add(paid));
        assertThat(balanceOf(after, a).paymentsReceived().toPlainString()).isEqualTo("20.50");
        assertThat(balanceOf(after, a).balance())
                .isEqualByComparingTo(balanceOf(before, a).balance().subtract(paid));
        // a payment only moves money between members: the stock-backed total stays
        assertThat(sumOfBalances(after)).isEqualByComparingTo(sumOfBalances(before));
        assertThat(after.inventoryValue()).isEqualTo(before.inventoryValue());
        // B owes less, so the suggestion shrinks by exactly what was paid
        assertThat(after.suggestions().getFirst().amount())
                .isEqualByComparingTo(before.suggestions().getFirst().amount().subtract(paid));

        // A (the recipient) deletes it again
        deletePayment(crew.adminEmail(), crew.teamId(), payment.paymentId());
        assertThat(balancesOf(crew.adminEmail(), crew.teamId())).isEqualTo(before);
        assertThat(paymentsOf(crew.adminEmail(), crew.teamId())).isEmpty();
    }

    @Test
    void thePaymentComesBackWithNamesAmountTimeNoteAndWhoCreatedIt() {
        final var crew = crew();
        final var a = crew.a();
        final var b = crew.b().memberId();
        final var paidAt = Instant.parse("2026-10-20T12:30:00Z");
        final var body = paymentBody(b, a, "12.3");
        body.put("paidAt", paidAt.toEpochMilli());
        body.put("note", "  cash at the lake  ");

        final var payment = recordPayment(crew.adminEmail(), crew.teamId(), body);

        final var names = new HashMap<Long, String>();
        members(crew.adminEmail(), crew.teamId())
                .forEach(member -> names.put(member.memberId(), member.displayName()));
        assertThat(payment.from())
                .isEqualTo(new MemberRef(b, Objects.requireNonNull(names.get(b))));
        assertThat(payment.to()).isEqualTo(new MemberRef(a, Objects.requireNonNull(names.get(a))));
        assertThat(payment.createdBy()).isEqualTo(payment.to());
        assertThat(payment.amount().toPlainString()).isEqualTo("12.30");
        assertThat(payment.paidAt()).isEqualTo(paidAt);
        assertThat(payment.note()).isEqualTo("cash at the lake");
        assertThat(paymentsOf(emailOf(crew.c()), crew.teamId())).containsExactly(payment);
    }

    @Test
    void paidAtDefaultsToNowAndTheNoteIsOptional() {
        final var crew = crew();
        final var before = Instant.now();

        final var payment =
                recordPayment(
                        crew.adminEmail(),
                        crew.teamId(),
                        paymentBody(crew.b().memberId(), crew.a(), "0.01"));

        assertThat(payment.paidAt()).isBetween(before, Instant.now());
        assertThat(payment.note()).isNull();
        assertThat(payment.amount().toPlainString()).isEqualTo("0.01");
    }

    @Test
    void theListIsNewestFirstByPaidAtThenByIdAndHoldsEveryPayment() {
        final var crew = crew();
        final var b = crew.b().memberId();
        final var oldest = recordAt(crew, b, 1, "1.00");
        final var newest1 = recordAt(crew, b, 3, "3.00");
        final var middle = recordAt(crew, b, 2, "2.00");
        final var newest2 = recordAt(crew, b, 3, "4.00");
        // no paging: more than a page of anything
        for (int i = 0; i < 55; i++) {
            recordAt(crew, b, 0, "0.01");
        }

        final var listed = paymentsOf(emailOf(crew.c()), crew.teamId());

        // same paidAt: the later id first
        assertThat(listed.subList(0, 4)).containsExactly(newest2, newest1, middle, oldest);
        assertThat(listed).hasSize(59);
        assertThat(listed.subList(4, 59))
                .extracting(Payment::paymentId)
                .isSortedAccordingTo((x, y) -> Long.compare(y, x));
    }

    @Test
    void aMemberWhoLeftCanStillSettleUpAndDisappearsFromTheBalancesOnceAtZero() {
        final var crew = crew();
        final var c = crew.c().memberId();
        fillFor(crew, c, 60);
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + crew.teamId() + "/members/me/leave",
                        emailOf(crew.c()),
                        null)
                .expectStatus()
                .isNoContent();
        final var owing = balanceOf(balancesOf(crew.adminEmail(), crew.teamId()), c);
        assertThat(owing.balance().toPlainString()).startsWith("-");
        final var debt = owing.balance().negate().toPlainString();

        // the admin records what the leaver hands over; the leaver is the payer
        final var payment =
                recordPayment(crew.adminEmail(), crew.teamId(), paymentBody(c, crew.a(), debt));

        assertThat(payment.from().memberId()).isEqualTo(c);
        final var settled = balancesOf(crew.adminEmail(), crew.teamId());
        assertThat(isListed(settled, c)).isFalse();
        assertThat(settled.suggestions()).isEmpty();
        assertThat(balanceOf(settled, crew.a()).paymentsReceived().toPlainString()).isEqualTo(debt);
        // deleting the payment brings the debt, and the leaver, back
        deletePayment(crew.adminEmail(), crew.teamId(), payment.paymentId());
        assertThat(balanceOf(balancesOf(crew.adminEmail(), crew.teamId()), c)).isEqualTo(owing);
    }

    @Test
    void aMemberCanBeThePayerOrTheRecipientWhenTheOtherPartyOrAnAdminRecordsIt() {
        final var crew = crew();
        final var another =
                addMember(crew.adminEmail(), crew.teamId(), uniqueEmail("another"), Role.MEMBER)
                        .memberId();
        final var b = crew.b().memberId();
        fillFor(crew, another, 60);

        // the payer's recipient (a plain member) records it, then an admin; the other way
        // round a plain member pays
        final var byRecipient =
                recordPayment(emailOf(crew.b()), crew.teamId(), paymentBody(another, b, "5.00"));
        final var byAdmin =
                recordPayment(crew.adminEmail(), crew.teamId(), paymentBody(another, b, "6.00"));
        final var toAnother =
                recordPayment(emailOf(crew.b()), crew.teamId(), paymentBody(b, another, "1.00"));

        assertThat(byRecipient.from().memberId()).isEqualTo(another);
        assertThat(byAdmin.from().memberId()).isEqualTo(another);
        assertThat(toAnother.to().memberId()).isEqualTo(another);
        final var balances = balancesOf(crew.adminEmail(), crew.teamId());
        assertThat(balanceOf(balances, another).paymentsSent().toPlainString()).isEqualTo("11.00");
        assertThat(balanceOf(balances, another).paymentsReceived().toPlainString())
                .isEqualTo("1.00");
        // B received 5.00 + 6.00 and sent 1.00
        assertThat(balanceOf(balances, b).balance().toPlainString()).isEqualTo("-10.00");
    }

    @Test
    void invalidPaymentsAreRejectedWithAMessageAndStoreNothing() {
        final var crew = crew();
        final var other = crew();
        final var a = crew.a();
        final var b = crew.b().memberId();
        final var rejected =
                List.of(
                        new Rejected(
                                "A payment needs two different members.", paymentBody(b, b, "5")),
                        new Rejected("Amount must be greater than 0.", paymentBody(b, a, "0")),
                        new Rejected("Amount must be greater than 0.", paymentBody(b, a, "-5.00")),
                        new Rejected(
                                "Amount can have at most 2 decimals.", paymentBody(b, a, "1.005")),
                        new Rejected(
                                "Amount must be below 1,000,000,000.",
                                paymentBody(b, a, "1000000000")),
                        new Rejected("Unknown member.", paymentBody(999_999_999L, a, "5.00")),
                        new Rejected("Unknown member.", paymentBody(b, 999_999_999L, "5.00")),
                        new Rejected("Unknown member.", paymentBody(other.a(), a, "5.00")),
                        new Rejected("Unknown member.", paymentBody(b, other.a(), "5.00")),
                        new Rejected(
                                "Note must be at most 200 characters.",
                                with(paymentBody(b, a, "5.00"), "note", "n".repeat(201))));
        for (final var bad : rejected) {
            assertThat(
                            badRequestDetail(
                                    paymentRequest(
                                            HttpMethod.POST,
                                            crew.adminEmail(),
                                            crew.teamId(),
                                            "",
                                            bad.body())))
                    .isEqualTo(bad.detail());
        }

        final var bodies = new ArrayList<Map<String, Object>>();
        // absent fields: no amount, no payer (0 is nobody's id), no recipient, neither
        bodies.add(without(paymentBody(b, a, "5.00"), "amount"));
        bodies.add(without(paymentBody(b, a, "5.00"), "fromMemberId"));
        bodies.add(without(paymentBody(b, a, "5.00"), "toMemberId"));
        bodies.add(without(without(paymentBody(b, a, "5.00"), "fromMemberId"), "toMemberId"));
        bodies.add(paymentBody(other.b().memberId(), other.c().memberId(), "5.00"));
        for (final var body : bodies) {
            paymentRequest(HttpMethod.POST, crew.adminEmail(), crew.teamId(), "", body)
                    .expectStatus()
                    .isBadRequest();
        }
        paymentRequest(HttpMethod.POST, crew.adminEmail(), crew.teamId(), "", "not json")
                .expectStatus()
                .isBadRequest();

        assertThat(paymentsOf(crew.adminEmail(), crew.teamId())).isEmpty();
        assertThat(paymentsOf(other.adminEmail(), other.teamId())).isEmpty();
        // the largest money that fits is fine, and so is trailing zeros
        final var largest =
                recordPayment(crew.adminEmail(), crew.teamId(), paymentBody(b, a, "999999999.99"));
        assertThat(largest.amount().toPlainString()).isEqualTo("999999999.99");
        assertThat(
                        recordPayment(crew.adminEmail(), crew.teamId(), paymentBody(b, a, "7.500"))
                                .amount()
                                .toPlainString())
                .isEqualTo("7.50");
    }

    private record Rejected(String detail, Map<String, Object> body) {}

    private Payment recordAt(
            final Crew crew, final long fromMemberId, final int daysAfterT0, final String amount) {
        final var body = paymentBody(fromMemberId, crew.a(), amount);
        body.put("paidAt", day(daysAfterT0).toEpochMilli());
        return recordPayment(crew.adminEmail(), crew.teamId(), body);
    }

    private static Map<String, Object> without(final Map<String, Object> body, final String key) {
        final var copy = new HashMap<>(body);
        copy.remove(key);
        return copy;
    }
}

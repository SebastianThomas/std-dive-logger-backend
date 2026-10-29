package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.support.AbstractPaymentIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

/** DOMAIN §10 for every balances and payments endpoint. */
final class PaymentAccessIntegrationTest extends AbstractPaymentIntegrationTest {

    private record Call(HttpMethod method, String path, @Nullable Object body) {}

    private record Actor(String email, long memberId) {}

    @Test
    void everyEndpointIsNotFoundForNonMembersLeftMembersAndUnknownTeams() {
        final var crew = crew();
        final var leaver = addActiveMember(crew.adminEmail(), crew.teamId());
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + crew.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var payment =
                recordPayment(
                        crew.adminEmail(),
                        crew.teamId(),
                        paymentBody(crew.b().memberId(), crew.a(), "5.00"));
        final var body = paymentBody(crew.b().memberId(), crew.a(), "1.00");

        for (final var caller : List.of(uniqueEmail("stranger"), emailOf(leaver))) {
            for (final var call : calls(crew.teamId(), payment.paymentId(), body)) {
                send(call.method(), call.path(), caller, call.body()).expectStatus().isNotFound();
            }
        }
        for (final var call : calls(999_999_999L, payment.paymentId(), body)) {
            send(call.method(), call.path(), crew.adminEmail(), call.body())
                    .expectStatus()
                    .isNotFound();
        }
        // nothing was recorded or deleted
        assertThat(paymentsOf(crew.adminEmail(), crew.teamId())).containsExactly(payment);
    }

    @Test
    void aPaymentOfAnotherTeamIsNotFoundThroughThisTeamsPathAndCountsOnlyThere() {
        final var crew = crew();
        final var other = crew();
        final var foreign =
                recordPayment(
                        other.adminEmail(),
                        other.teamId(),
                        paymentBody(other.b().memberId(), other.a(), "9.00"));

        paymentRequest(
                        HttpMethod.DELETE,
                        crew.adminEmail(),
                        crew.teamId(),
                        "/" + foreign.paymentId(),
                        null)
                .expectStatus()
                .isNotFound();

        assertThat(paymentsOf(crew.adminEmail(), crew.teamId())).isEmpty();
        assertThat(paymentsOf(other.adminEmail(), other.teamId())).containsExactly(foreign);
        final var here = balancesOf(crew.adminEmail(), crew.teamId());
        final var there = balancesOf(other.adminEmail(), other.teamId());
        assertThat(balanceOf(here, crew.a()).paymentsReceived().toPlainString()).isEqualTo("0.00");
        assertThat(balanceOf(there, other.a()).paymentsReceived().toPlainString())
                .isEqualTo("9.00");
    }

    @Test
    void onlyThePayerTheRecipientOrAnAdminRecordsAndDeletesAPayment() {
        final var crew = crew();
        final var team = crew.teamId();
        final var payer = new Actor(emailOf(crew.b()), crew.b().memberId());
        final var recipient = new Actor(emailOf(crew.c()), crew.c().memberId());
        final var admin = new Actor(crew.adminEmail(), crew.a());
        final var bystander = addActiveMember(crew.adminEmail(), team);
        final var body = paymentBody(payer.memberId(), recipient.memberId(), "5.00");

        // an unrelated member can neither record ...
        paymentRequest(HttpMethod.POST, emailOf(bystander), team, "", body)
                .expectStatus()
                .isForbidden();
        // ... nor delete the payment of two others, but may read the list and the balances
        final var existing = recordPayment(payer.email(), team, body);
        paymentRequest(
                        HttpMethod.DELETE,
                        emailOf(bystander),
                        team,
                        "/" + existing.paymentId(),
                        null)
                .expectStatus()
                .isForbidden();
        assertThat(paymentsOf(emailOf(bystander), team)).containsExactly(existing);
        assertThat(balancesOf(emailOf(bystander), team).members()).hasSize(4);

        // the payer, the recipient and an admin each record one and delete one they did not record
        for (final var actor : List.of(payer, recipient, admin)) {
            final var recorded = recordPayment(actor.email(), team, body);
            assertThat(recorded.createdBy().memberId()).isEqualTo(actor.memberId());
            deletePayment(actor.email(), team, recorded.paymentId());
            final var byOther = recordPayment(admin.email(), team, body);
            deletePayment(actor.email(), team, byOther.paymentId());
        }
        assertThat(paymentsOf(admin.email(), team)).containsExactly(existing);
        // the bystander's attempts changed nothing
        assertThat(balanceOf(balancesOf(admin.email(), team), payer.memberId()).paymentsSent())
                .isEqualByComparingTo("5.00");
    }

    @Test
    void anUnknownPaymentIsNotFoundAndADeletedOneIsGone() {
        final var crew = crew();
        final var payment =
                recordPayment(
                        crew.adminEmail(),
                        crew.teamId(),
                        paymentBody(crew.b().memberId(), crew.a(), "5.00"));

        paymentRequest(HttpMethod.DELETE, crew.adminEmail(), crew.teamId(), "/999999999", null)
                .expectStatus()
                .isNotFound();
        deletePayment(crew.adminEmail(), crew.teamId(), payment.paymentId());
        paymentRequest(
                        HttpMethod.DELETE,
                        crew.adminEmail(),
                        crew.teamId(),
                        "/" + payment.paymentId(),
                        null)
                .expectStatus()
                .isNotFound();
    }

    private static List<Call> calls(
            final long teamId, final long paymentId, final Map<String, Object> body) {
        return List.of(
                new Call(HttpMethod.GET, balancesUri(teamId), null),
                new Call(HttpMethod.GET, paymentsUri(teamId), null),
                new Call(HttpMethod.POST, paymentsUri(teamId), body),
                new Call(HttpMethod.DELETE, paymentsUri(teamId) + "/" + paymentId, null));
    }
}

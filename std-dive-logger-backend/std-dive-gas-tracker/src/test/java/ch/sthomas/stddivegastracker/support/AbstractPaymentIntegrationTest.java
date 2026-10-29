package ch.sthomas.stddivegastracker.support;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.fill.Fill;
import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.payment.Balances.MemberBalance;
import ch.sthomas.stddivegastracker.model.payment.Payment;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.vessel.VesselDetail;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Balances and payments over HTTP (D5): a small crew with one open O2 bank, fills that charge them,
 * request bodies and readers. Amounts are asserted as plain strings (exact scale).
 */
public abstract class AbstractPaymentIntegrationTest extends AbstractFillIntegrationTest {

    /** Cost of the bank {@link #crew} buys; paid by A. */
    protected static final BigDecimal BANK_COST = new BigDecimal("600.00");

    /**
     * Admin A (pays for the bank) with active members B and C and an open TRUE_UP O2 bank (50 L,
     * 200 bar, residual 20 bar, {@link #BANK_COST}).
     */
    protected record Crew(
            String adminEmail, long teamId, long a, Member b, Member c, VesselDetail bank) {}

    protected Crew crew() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var b = addActiveMember(admin, team.teamId());
        final var c = addActiveMember(admin, team.teamId());
        final var bank = createO2Bank(admin, team.teamId(), team.memberId(), "TRUE_UP");
        return new Crew(admin, team.teamId(), team.memberId(), b, c, bank);
    }

    /** A 12 L cylinder charged to {@code memberId}, filled from the crew's bank, as the admin. */
    protected Fill fillFor(final Crew crew, final long memberId, final double pressureAfterBar) {
        return createFill(
                crew.adminEmail(),
                crew.teamId(),
                at(
                        fillBody(
                                cylinder(12, memberId),
                                0,
                                fromBank(crew.bank().vesselId(), pressureAfterBar)),
                        day(1)));
    }

    /** What a fill drew from its bank (one stage, no line loss in a default team), in litres. */
    protected static BigDecimal drawnBy(final Fill... fills) {
        return List.of(fills).stream()
                .map(fill -> bd(fill.stages().getFirst().drawLitres()))
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MC));
    }

    protected void close(final Crew crew) {
        send(
                        HttpMethod.POST,
                        vesselsUri(crew.teamId()) + "/" + crew.bank().vesselId() + "/close",
                        crew.adminEmail(),
                        Map.of("closingPressureBar", 25))
                .expectStatus()
                .isOk();
    }

    // --- requests -----------------------------------------------------------------------

    protected static String balancesUri(final long teamId) {
        return "/v1/teams/" + teamId + "/balances";
    }

    protected static String paymentsUri(final long teamId) {
        return "/v1/teams/" + teamId + "/payments";
    }

    protected Balances balancesOf(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, balancesUri(teamId), email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(Balances.class)
                        .returnResult()
                        .getResponseBody());
    }

    protected List<Payment> paymentsOf(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, paymentsUri(teamId), email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<List<Payment>>() {})
                        .returnResult()
                        .getResponseBody());
    }

    protected ResponseSpec paymentRequest(
            final HttpMethod method,
            final String email,
            final long teamId,
            final String pathAfterPayments,
            final @Nullable Object body) {
        return send(method, paymentsUri(teamId) + pathAfterPayments, email, body);
    }

    protected Payment recordPayment(
            final String email, final long teamId, final Map<String, Object> body) {
        return Objects.requireNonNull(
                paymentRequest(HttpMethod.POST, email, teamId, "", body)
                        .expectStatus()
                        .isOk()
                        .expectBody(Payment.class)
                        .returnResult()
                        .getResponseBody());
    }

    protected void deletePayment(final String email, final long teamId, final long paymentId) {
        paymentRequest(HttpMethod.DELETE, email, teamId, "/" + paymentId, null)
                .expectStatus()
                .isNoContent();
    }

    /** The detail of a 400 response. */
    protected static String badRequestDetail(final ResponseSpec response) {
        return Objects.requireNonNull(
                Objects.requireNonNull(
                                response.expectStatus()
                                        .isBadRequest()
                                        .expectBody(ProblemDetail.class)
                                        .returnResult()
                                        .getResponseBody())
                        .getDetail());
    }

    // --- bodies and readers -------------------------------------------------------------

    protected static Map<String, Object> paymentBody(
            final long fromMemberId, final long toMemberId, final String amount) {
        final var body = new HashMap<String, Object>();
        body.put("fromMemberId", fromMemberId);
        body.put("toMemberId", toMemberId);
        body.put("amount", amount);
        return body;
    }

    /** The row of a member who must be listed. */
    protected static MemberBalance balanceOf(final Balances balances, final long memberId) {
        return balances.members().stream()
                .filter(row -> row.member().memberId() == memberId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Member " + memberId + " is not listed"));
    }

    protected static boolean isListed(final Balances balances, final long memberId) {
        return balances.members().stream().anyMatch(row -> row.member().memberId() == memberId);
    }

    protected static BigDecimal sumOfBalances(final Balances balances) {
        return balances.members().stream()
                .map(MemberBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    protected static BigDecimal oxygenInflow() {
        return bd(content(200, GasMix.OXYGEN));
    }

    protected static BigDecimal oxygenResidual() {
        return bd(content(20, GasMix.OXYGEN));
    }
}

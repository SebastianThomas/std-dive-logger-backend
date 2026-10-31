package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.scrubber.CanisterStatus;
import ch.sthomas.stddivegastracker.model.scrubber.ScrubberCanister;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.support.AbstractVesselIntegrationTest;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** DOMAIN §12, API.md §7: shared absorbent canisters, their money and the tracking switches. */
final class ScrubberControllerIntegrationTest extends AbstractVesselIntegrationTest {

    @Test
    void usesAreEstimatedChargesUntilEmptyThenTheWholeCostIsShared() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var canister = canister(admin, team.teamId(), team.memberId(), "230.00");
        assertThat(canister.capacityKg()).isEqualTo(23);
        assertThat(canister.status()).isEqualTo(CanisterStatus.OPEN);
        assertThat(canister.pricePerKg()).isEqualByComparingTo("10");
        assertThat(canister.isEstimate()).isTrue();

        final var used = use(admin, team.teamId(), canister, diver.memberId(), 15);
        assertThat(used.recordedKg()).isEqualTo(15);
        assertThat(used.remainingKg()).isEqualTo(8);
        assertThat(used.uses().getFirst().charge()).isEqualByComparingTo("150.00");
        assertThat(balanceOf(admin, team.teamId(), diver.memberId()).scrubberCharges())
                .isEqualByComparingTo("150.00");
        final var creditor = balanceOf(admin, team.teamId(), team.memberId());
        assertThat(creditor.scrubberPaid()).isEqualByComparingTo("230.00");
        assertThat(creditor.balance()).isEqualByComparingTo("230.00");
        final var debtor = balanceOf(admin, team.teamId(), diver.memberId());
        assertThat(debtor.balance()).isEqualByComparingTo("-150.00");
        assertThat(debtor.provisionalCharges()).isEqualByComparingTo("150.00");
        assertThat(balances(admin, team.teamId()).inventoryValue()).isEqualByComparingTo("80.00");

        final var empty =
                ok(
                        send(
                                HttpMethod.POST,
                                uri(team.teamId(), canister) + "/empty",
                                emailOf(diver),
                                null));

        assertThat(empty.status()).isEqualTo(CanisterStatus.EMPTY);
        assertThat(empty.remainingKg()).isZero();
        assertThat(empty.isEstimate()).isFalse();
        assertThat(empty.uses().getFirst().charge()).isEqualByComparingTo("230.00");
        final var settled = balanceOf(admin, team.teamId(), diver.memberId());
        assertThat(settled.balance()).isEqualByComparingTo("-230.00");
        assertThat(settled.provisionalCharges()).isEqualByComparingTo("0.00");
        assertThat(balances(admin, team.teamId()).inventoryValue()).isEqualByComparingTo("0.00");
        assertThat(balances(admin, team.teamId()).suggestions()).hasSize(1);
    }

    @Test
    void anEmptyCanisterTakesNoUseUntilAnAdminReopensIt() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var canister = canister(admin, team.teamId(), team.memberId(), "230.00");
        ok(send(HttpMethod.POST, uri(team.teamId(), canister) + "/empty", admin, null));

        useRequest(emailOf(diver), team.teamId(), canister, diver.memberId(), 2.5)
                .expectStatus()
                .isBadRequest();
        send(HttpMethod.POST, uri(team.teamId(), canister) + "/reopen", emailOf(diver), null)
                .expectStatus()
                .isForbidden();
        send(HttpMethod.POST, uri(team.teamId(), canister) + "/empty", admin, null)
                .expectStatus()
                .isEqualTo(409);

        final var reopened =
                ok(send(HttpMethod.POST, uri(team.teamId(), canister) + "/reopen", admin, null));
        assertThat(reopened.status()).isEqualTo(CanisterStatus.OPEN);
        use(emailOf(diver), team.teamId(), canister, diver.memberId(), 2.5);
    }

    @Test
    void severalPeopleCanPayAndOnlyAnAdminBooksSomeoneElsesCost() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var body = new HashMap<String, Object>();
        body.put("label", "Sofno 2");
        body.put("capacityKg", 20);
        body.put(
                "initialCostLines",
                List.of(
                        Map.of("payerMemberId", team.memberId(), "amount", "100.00"),
                        Map.of("payerMemberId", diver.memberId(), "amount", "60.00")));
        final var shared = ok(send(HttpMethod.POST, uri(team.teamId()), admin, body));
        assertThat(shared.totalCost()).isEqualByComparingTo("160.00");
        assertThat(shared.pricePerKg()).isEqualByComparingTo("8");

        send(HttpMethod.POST, uri(team.teamId()), emailOf(diver), body)
                .expectStatus()
                .isForbidden();
        send(
                        HttpMethod.POST,
                        uri(team.teamId(), shared) + "/cost-lines",
                        emailOf(diver),
                        Map.of("payerMemberId", team.memberId(), "amount", "5.00"))
                .expectStatus()
                .isForbidden();
        final var more =
                ok(
                        send(
                                HttpMethod.POST,
                                uri(team.teamId(), shared) + "/cost-lines",
                                emailOf(diver),
                                Map.of("payerMemberId", diver.memberId(), "amount", "5.00")));
        assertThat(more.costLines()).hasSize(3);
        final var lineId = more.costLines().getLast().costLineId();
        send(
                        HttpMethod.DELETE,
                        uri(team.teamId(), shared) + "/cost-lines/" + lineId,
                        emailOf(diver),
                        null)
                .expectStatus()
                .isForbidden();
        assertThat(
                        ok(send(
                                        HttpMethod.DELETE,
                                        uri(team.teamId(), shared) + "/cost-lines/" + lineId,
                                        admin,
                                        null))
                                .costLines())
                .hasSize(2);
    }

    @Test
    void aWrongUseGoesByItsMemberItsRecorderOrAnAdminButNobodyElse() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addActiveMember(admin, team.teamId());
        final var other = addActiveMember(admin, team.teamId());
        final var canister = canister(admin, team.teamId(), team.memberId(), "230.00");
        final var used = use(admin, team.teamId(), canister, diver.memberId(), 2.5);
        final var useId = used.uses().getFirst().useId();
        final var useUri = uri(team.teamId(), canister) + "/uses/" + useId;

        send(HttpMethod.DELETE, useUri, emailOf(other), null).expectStatus().isForbidden();
        assertThat(ok(send(HttpMethod.DELETE, useUri, emailOf(diver), null)).uses()).isEmpty();
        send(HttpMethod.DELETE, useUri, admin, null).expectStatus().isNotFound();
    }

    @Test
    void aBadUseIsRefusedAndAnotherTeamsCanisterDoesNotExist() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var canister = canister(admin, team.teamId(), team.memberId(), "230.00");
        useRequest(admin, team.teamId(), canister, 999_999_999L, 2.5).expectStatus().isBadRequest();
        useRequest(admin, team.teamId(), canister, team.memberId(), 0)
                .expectStatus()
                .isBadRequest();
        useRequest(admin, team.teamId(), canister, team.memberId(), 1001)
                .expectStatus()
                .isBadRequest();

        final var stranger = uniqueEmail("stranger");
        final var otherTeam = createTeam(stranger);
        send(HttpMethod.GET, uri(otherTeam.teamId(), canister), stranger, null)
                .expectStatus()
                .isNotFound();
        send(HttpMethod.GET, uri(team.teamId(), canister), stranger, null)
                .expectStatus()
                .isNotFound();
    }

    @Test
    void eachTrackingCanBeSwitchedOffAloneAndWhatExistsStaysInTheBalances() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        assertThat(team.gasTrackingEnabled()).isTrue();
        assertThat(team.scrubberTrackingEnabled()).isTrue();
        final var canister = canister(admin, team.teamId(), team.memberId(), "230.00");
        use(admin, team.teamId(), canister, team.memberId(), 2.3);

        final var scrubberOff = updateSwitches(admin, team.teamId(), true, false);
        assertThat(scrubberOff.gasTrackingEnabled()).isTrue();
        assertThat(scrubberOff.scrubberTrackingEnabled()).isFalse();
        send(HttpMethod.POST, uri(team.teamId()), admin, Map.of("label", "Another"))
                .expectStatus()
                .isEqualTo(409);
        useRequest(admin, team.teamId(), canister, team.memberId(), 2.5)
                .expectStatus()
                .isEqualTo(409);
        assertThat(balanceOf(admin, team.teamId(), team.memberId()).scrubberCharges())
                .isEqualByComparingTo("23.00");
        createBank(admin, team.teamId(), "He bank");

        final var gasOff = updateSwitches(admin, team.teamId(), false, true);
        assertThat(gasOff.gasTrackingEnabled()).isFalse();
        assertThat(gasOff.scrubberTrackingEnabled()).isTrue();
        createBankRequest(admin, team.teamId(), "O2 bank").expectStatus().isEqualTo(409);
        use(admin, team.teamId(), canister, team.memberId(), 2.5);

        // a save that does not mention the switches leaves them
        final var kept =
                Objects.requireNonNull(
                        send(HttpMethod.PUT, "/v1/teams/" + team.teamId(), admin, teamBody())
                                .expectStatus()
                                .isOk()
                                .expectBody(Team.class)
                                .returnResult()
                                .getResponseBody());
        assertThat(kept.gasTrackingEnabled()).isFalse();
        assertThat(kept.scrubberTrackingEnabled()).isTrue();
    }

    private Team updateSwitches(
            final String admin, final long teamId, final boolean gas, final boolean scrubber) {
        final var body = teamBody();
        body.put("gasTrackingEnabled", gas);
        body.put("scrubberTrackingEnabled", scrubber);
        return Objects.requireNonNull(
                send(HttpMethod.PUT, "/v1/teams/" + teamId, admin, body)
                        .expectStatus()
                        .isOk()
                        .expectBody(Team.class)
                        .returnResult()
                        .getResponseBody());
    }

    private static Map<String, Object> teamBody() {
        final var body = new HashMap<String, Object>();
        body.put("name", "Team");
        body.put("defaultLossPolicy", "TRUE_UP");
        return body;
    }

    private void createBank(final String admin, final long teamId, final String label) {
        createBankRequest(admin, teamId, label).expectStatus().isOk();
    }

    private ResponseSpec createBankRequest(
            final String admin, final long teamId, final String label) {
        return send(
                HttpMethod.POST,
                vesselsUri(teamId),
                admin,
                Map.of("label", label, "waterVolumeLitres", 50, "mix", Map.of("o2", 1, "he", 0)));
    }

    private ScrubberCanister canister(
            final String email, final long teamId, final long payerId, final String amount) {
        final var body = new HashMap<String, Object>();
        body.put("label", "Sofno 1");
        body.put("initialCostLines", List.of(Map.of("payerMemberId", payerId, "amount", amount)));
        return ok(send(HttpMethod.POST, uri(teamId), email, body));
    }

    private ScrubberCanister use(
            final String email,
            final long teamId,
            final ScrubberCanister canister,
            final long memberId,
            final double kg) {
        return ok(useRequest(email, teamId, canister, memberId, kg));
    }

    private ResponseSpec useRequest(
            final String email,
            final long teamId,
            final ScrubberCanister canister,
            final long memberId,
            final double kg) {
        return send(
                HttpMethod.POST,
                uri(teamId, canister) + "/uses",
                email,
                Map.of("memberId", memberId, "kg", kg));
    }

    private Balances balances(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, "/v1/teams/" + teamId + "/balances", email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<Balances>() {})
                        .returnResult()
                        .getResponseBody());
    }

    private Balances.MemberBalance balanceOf(
            final String email, final long teamId, final long memberId) {
        return balances(email, teamId).members().stream()
                .filter(row -> row.member().memberId() == memberId)
                .findFirst()
                .orElseThrow();
    }

    private static ScrubberCanister ok(final ResponseSpec response) {
        return Objects.requireNonNull(
                response.expectStatus()
                        .isOk()
                        .expectBody(ScrubberCanister.class)
                        .returnResult()
                        .getResponseBody());
    }

    private static String uri(final long teamId) {
        return "/v1/teams/" + teamId + "/scrubbers";
    }

    private static String uri(final long teamId, final @Nullable ScrubberCanister canister) {
        return uri(teamId) + "/" + Objects.requireNonNull(canister).canisterId();
    }
}

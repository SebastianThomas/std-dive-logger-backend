package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.Page;
import ch.sthomas.stddivegastracker.model.fill.FillSummary;
import ch.sthomas.stddivegastracker.model.payment.Balances;
import ch.sthomas.stddivegastracker.model.report.LossesReport;
import ch.sthomas.stddivegastracker.model.team.TeamSummary;
import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.model.vessel.VesselSummary;
import ch.sthomas.stddivegastracker.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The local demo data (profile demo-seed): reachable through dev login, consistent, seeded once.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev-login,demo-seed",
            "spring.docker.compose.enabled=false",
            "ch.sthomas.stddivegastracker.auth.require-email-verified-claim=true",
            "ch.sthomas.stddivegastracker.auth.dev-login-enabled=true",
        })
final class DemoSeedIntegrationTest extends AbstractIntegrationTest {

    @Autowired private DemoSeed demoSeed;
    @Autowired private TransactionTemplate transaction;

    @Test
    void theDemoAdminSeesAConsistentDemoTeamThroughDevLogin() {
        final var auth = "Bearer " + devLogin(DemoSeed.ADMIN_EMAIL);
        final var team = demoTeam(get(auth, "/v1/me", MeResponse.class));
        final var base = "/v1/teams/" + team.teamId();

        final var banks =
                get(
                        auth,
                        base + "/vessels?status=ALL",
                        new ParameterizedTypeReference<List<VesselSummary>>() {});
        assertThat(banks)
                .extracting(VesselSummary::label)
                .containsExactlyInAnyOrder("He bank", "O2 bank #2", "EAN32 bank", "O2 bank #1");
        assertThat(banks)
                .filteredOn(bank -> bank.label().equals("EAN32 bank"))
                .singleElement()
                .satisfies(bank -> assertThat(bank.mix().o2()).isEqualTo(0.32));

        final var fills =
                get(
                        auth,
                        base + "/fills?size=200",
                        new ParameterizedTypeReference<Page<FillSummary>>() {});
        assertThat(fills.totalElements()).isEqualTo(13);

        final var balances = get(auth, base + "/balances", Balances.class);
        assertThat(balances.members()).hasSize(3);
        final var sum =
                balances.members().stream()
                        .map(Balances.MemberBalance::balance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Σ balances = gas still in stock (DOMAIN §7.5 #3), one rounding cent per member
        assertThat(sum.subtract(balances.inventoryValue()).abs())
                .isLessThanOrEqualTo(new BigDecimal("0.03"));

        assertThat(get(auth, base + "/reports/losses", LossesReport.class).vessels())
                .singleElement()
                .satisfies(entry -> assertThat(entry.vessel().label()).isEqualTo("O2 bank #1"));
    }

    @Test
    void seedingAgainChangesNothing() {
        final var auth = "Bearer " + devLogin(DemoSeed.ADMIN_EMAIL);
        final var before = get(auth, "/v1/me", MeResponse.class);

        transaction.executeWithoutResult(_ -> demoSeed.seed(Instant.now()));

        final var after = get(auth, "/v1/me", MeResponse.class);
        assertThat(after.teams()).isEqualTo(before.teams());
        assertThat(after.teams()).filteredOn(t -> t.name().equals(DemoSeed.TEAM_NAME)).hasSize(1);
    }

    private static TeamSummary demoTeam(final MeResponse me) {
        return me.teams().stream()
                .filter(team -> team.name().equals(DemoSeed.TEAM_NAME))
                .findFirst()
                .orElseThrow();
    }

    private String devLogin(final String email) {
        return Objects.requireNonNull(
                        client.post()
                                .uri("/api/dev/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of("email", email))
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(DevLoginController.DevLoginResponse.class)
                                .returnResult()
                                .getResponseBody())
                .accessToken();
    }

    private <T> T get(final String authorization, final String uri, final Class<T> type) {
        return Objects.requireNonNull(
                client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(type)
                        .returnResult()
                        .getResponseBody());
    }

    private <T> T get(
            final String authorization,
            final String uri,
            final ParameterizedTypeReference<T> type) {
        return Objects.requireNonNull(
                client.get()
                        .uri(uri)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody(type)
                        .returnResult()
                        .getResponseBody());
    }
}

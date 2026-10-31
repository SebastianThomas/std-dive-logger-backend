package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamSummary;
import ch.sthomas.stddivegastracker.support.AbstractTeamIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class TeamControllerIntegrationTest extends AbstractTeamIntegrationTest {

    private static final Map<String, Object> VALID =
            Map.of("name", "Blenders", "defaultLossPolicy", "POOLED");

    @Test
    void creatingATeamMakesTheCreatorItsAdminWithTheDefaults() {
        final var admin = uniqueEmail("admin");

        final var team =
                createTeam(admin, Map.of("name", "  Blenders  ", "defaultLossPolicy", "POOLED"));

        assertThat(team.teamId()).isPositive();
        assertThat(team.memberId()).isPositive();
        assertThat(team.name()).isEqualTo("Blenders");
        assertThat(team.role()).isEqualTo(Role.ADMIN);
        assertThat(team.currency()).isEqualTo("CHF");
        assertThat(team.defaultLossPolicy()).isEqualTo(LossPolicy.POOLED);
        assertThat(team.lineDeadVolumeLitres()).isZero();
        assertThat(team.defaultResidualPressureBar()).isZero();
        assertThat(me(admin).teams())
                .containsExactly(
                        new TeamSummary(team.teamId(), "Blenders", Role.ADMIN, team.memberId()));
    }

    @Test
    void allSettingsAreStoredAndTheCurrencyIsUpperCased() {
        final var admin = uniqueEmail("admin");

        final var team =
                createTeam(
                        admin,
                        Map.of(
                                "name", "Euro divers",
                                "currency", " eur ",
                                "defaultLossPolicy", "TRUE_UP",
                                "lineDeadVolumeLitres", 0.35,
                                "defaultResidualPressureBar", 40));

        assertThat(team.currency()).isEqualTo("EUR");
        assertThat(team.defaultLossPolicy()).isEqualTo(LossPolicy.TRUE_UP);
        assertThat(team.lineDeadVolumeLitres()).isEqualTo(0.35);
        assertThat(team.defaultResidualPressureBar()).isEqualTo(40);
        assertThat(getTeam(admin, team.teamId())).isEqualTo(team);
    }

    @Test
    void theLimitsThemselvesAreAccepted() {
        final var team =
                createTeam(
                        uniqueEmail("admin"),
                        Map.of(
                                "name",
                                "x".repeat(60),
                                "defaultLossPolicy",
                                "TRUE_UP",
                                "lineDeadVolumeLitres",
                                10,
                                "defaultResidualPressureBar",
                                400));

        assertThat(team.name()).hasSize(60);
        assertThat(team.lineDeadVolumeLitres()).isEqualTo(10);
        assertThat(team.defaultResidualPressureBar()).isEqualTo(400);
    }

    @Test
    void anActiveMemberReadsTheTeamWithTheirOwnRoleAndMemberId() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);

        final var seen = getTeam(emailOf(diver), team.teamId());

        assertThat(seen.role()).isEqualTo(Role.MEMBER);
        assertThat(seen.memberId()).isEqualTo(diver.memberId()).isNotEqualTo(team.memberId());
        assertThat(seen.name()).isEqualTo(team.name());
    }

    @Test
    void readingATeamIsNotFoundForNonMembersLeftMembersAndUnknownTeams() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.MEMBER);
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();

        for (final var caller : List.of(uniqueEmail("stranger"), emailOf(leaver))) {
            send(HttpMethod.GET, "/v1/teams/" + team.teamId(), caller, null)
                    .expectStatus()
                    .isNotFound();
        }
        send(HttpMethod.GET, "/v1/teams/999999999", admin, null).expectStatus().isNotFound();
    }

    @Test
    void anAdminUpdatesTheSettingsAndEveryMemberSeesThem() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);

        final var updated =
                Objects.requireNonNull(
                        send(
                                        HttpMethod.PUT,
                                        "/v1/teams/" + team.teamId(),
                                        admin,
                                        Map.of(
                                                "name", " Renamed ",
                                                "currency", "usd",
                                                "defaultLossPolicy", "POOLED",
                                                "lineDeadVolumeLitres", 0.5,
                                                "defaultResidualPressureBar", 25))
                                .expectStatus()
                                .isOk()
                                .expectBody(Team.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(updated.teamId()).isEqualTo(team.teamId());
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.currency()).isEqualTo("USD");
        assertThat(updated.defaultLossPolicy()).isEqualTo(LossPolicy.POOLED);
        assertThat(updated.lineDeadVolumeLitres()).isEqualTo(0.5);
        assertThat(updated.defaultResidualPressureBar()).isEqualTo(25);
        assertThat(updated.role()).isEqualTo(Role.ADMIN);
        assertThat(getTeam(emailOf(diver), team.teamId()))
                .isEqualTo(
                        new Team(
                                team.teamId(),
                                "Renamed",
                                Role.MEMBER,
                                diver.memberId(),
                                "USD",
                                LossPolicy.POOLED,
                                0.5,
                                25,
                                true,
                                true));
    }

    @Test
    void updatingATeamNeedsAnAdmin() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.ADMIN);
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members/me/leave",
                        emailOf(leaver),
                        null)
                .expectStatus()
                .isNoContent();
        final var uri = "/v1/teams/" + team.teamId();

        send(HttpMethod.PUT, uri, emailOf(diver), VALID).expectStatus().isForbidden();
        send(HttpMethod.PUT, uri, uniqueEmail("stranger"), VALID).expectStatus().isNotFound();
        send(HttpMethod.PUT, uri, emailOf(leaver), VALID).expectStatus().isNotFound();
        send(HttpMethod.PUT, "/v1/teams/999999999", admin, VALID).expectStatus().isNotFound();
        assertThat(getTeam(admin, team.teamId()).name()).isEqualTo("Team");
    }

    @Test
    void invalidSettingsAreBadRequestsWithAShortMessage() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        assertBadRequest(admin, with("name", "   "), "Name");
        assertBadRequest(admin, with("name", "x".repeat(61)), "Name");
        assertBadRequest(admin, without("name"), "Name");
        assertBadRequest(admin, with("currency", "CH"), "Currency");
        assertBadRequest(admin, with("currency", "CHFX"), "Currency");
        assertBadRequest(admin, with("currency", "C1F"), "Currency");
        assertBadRequest(admin, without("defaultLossPolicy"), "Loss policy");
        assertBadRequest(admin, with("defaultLossPolicy", "SOMETIMES"), "");
        assertBadRequest(admin, with("lineDeadVolumeLitres", -0.1), "Line dead volume");
        assertBadRequest(admin, with("lineDeadVolumeLitres", 10.1), "Line dead volume");
        assertBadRequest(admin, with("defaultResidualPressureBar", -1), "Residual pressure");
        assertBadRequest(admin, with("defaultResidualPressureBar", 400.5), "Residual pressure");
        // The same rules guard the update; nothing was stored.
        send(HttpMethod.PUT, "/v1/teams/" + team.teamId(), admin, with("name", ""))
                .expectStatus()
                .isBadRequest();
        assertThat(getTeam(admin, team.teamId()).name()).isEqualTo("Team");
    }

    @Test
    void meListsOnlyActiveTeamsSortedByNameThenId() {
        final var user = uniqueEmail("user");
        final var zulu = createTeam(user, Map.of("name", "zulu", "defaultLossPolicy", "TRUE_UP"));
        final var bravo = createTeam(user, Map.of("name", "Bravo", "defaultLossPolicy", "TRUE_UP"));
        final var alphaOne =
                createTeam(user, Map.of("name", "alpha", "defaultLossPolicy", "TRUE_UP"));
        final var alphaTwo =
                createTeam(user, Map.of("name", "alpha", "defaultLossPolicy", "TRUE_UP"));
        final var left =
                createTeam(user, Map.of("name", "AAA left", "defaultLossPolicy", "TRUE_UP"));
        final var otherAdmin = uniqueEmail("other");
        addMember(user, left.teamId(), otherAdmin, Role.ADMIN);
        // Their first sign-in turns the invitation into an ACTIVE admin, so the creator may leave.
        me(otherAdmin);
        send(HttpMethod.POST, "/v1/teams/" + left.teamId() + "/members/me/leave", user, null)
                .expectStatus()
                .isNoContent();
        createTeam(otherAdmin);

        assertThat(me(user).teams())
                .extracting(TeamSummary::teamId)
                .containsExactly(
                        alphaOne.teamId(), alphaTwo.teamId(), bravo.teamId(), zulu.teamId());
    }

    private Team getTeam(final String email, final long teamId) {
        return Objects.requireNonNull(
                send(HttpMethod.GET, "/v1/teams/" + teamId, email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(Team.class)
                        .returnResult()
                        .getResponseBody());
    }

    private void assertBadRequest(
            final String email, final Map<String, Object> body, final String messagePart) {
        send(HttpMethod.POST, "/v1/teams", email, body)
                .expectStatus()
                .isBadRequest()
                .expectBody(String.class)
                .value(detail -> assertThat(detail).contains(messagePart));
    }

    private static Map<String, Object> with(final String key, final Object value) {
        final var body = new HashMap<>(VALID);
        body.put(key, value);
        return body;
    }

    private static Map<String, Object> without(final String key) {
        final var body = new HashMap<>(VALID);
        body.remove(key);
        return body;
    }
}

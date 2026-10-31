package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.support.AbstractTeamIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class MemberControllerIntegrationTest extends AbstractTeamIntegrationTest {

    private static final String LAST_ADMIN = "at least one admin";

    @Autowired JdbcTemplate jdbc;

    // --- list ---------------------------------------------------------------------------

    @Test
    void membersAreListedActiveFirstThenLeftEachByDisplayNameIgnoringCaseThenId() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        setDisplayName(admin, "Mallory");
        final var bob = addMember(admin, team.teamId(), uniqueEmail("bob"), Role.MEMBER);
        setDisplayName(emailOf(bob), "bob");
        final var alice = addMember(admin, team.teamId(), uniqueEmail("alice"), Role.MEMBER);
        setDisplayName(emailOf(alice), "Alice");
        final var aliceToo = addMember(admin, team.teamId(), uniqueEmail("alice2"), Role.ADMIN);
        setDisplayName(emailOf(aliceToo), "alice");
        final var aaron = addMember(admin, team.teamId(), uniqueEmail("aaron"), Role.MEMBER);
        setDisplayName(emailOf(aaron), "Aaron");
        final var zed = addMember(admin, team.teamId(), uniqueEmail("zed"), Role.MEMBER);
        setDisplayName(emailOf(zed), "Zed");
        update(admin, team.teamId(), zed.memberId(), Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();
        update(admin, team.teamId(), aaron.memberId(), Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();

        final var list = members(emailOf(bob), team.teamId());

        // Active: Alice (first added) before alice (same name, higher id), bob, Mallory;
        // then the LEFT ones: Aaron, Zed - although "Aaron" would sort first overall.
        assertThat(list)
                .extracting(Member::memberId)
                .containsExactly(
                        alice.memberId(),
                        aliceToo.memberId(),
                        bob.memberId(),
                        team.memberId(),
                        aaron.memberId(),
                        zed.memberId());
        assertThat(list)
                .extracting(Member::status)
                .containsExactly(
                        MemberStatus.ACTIVE,
                        MemberStatus.ACTIVE,
                        MemberStatus.ACTIVE,
                        MemberStatus.ACTIVE,
                        MemberStatus.LEFT,
                        MemberStatus.LEFT);
        assertThat(list.get(5).leftAt()).isNotNull();
        assertThat(list.get(0).leftAt()).isNull();
    }

    @Test
    void aMemberRecordCarriesTheApiFields() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        final var body =
                Objects.requireNonNull(
                        send(HttpMethod.GET, "/v1/teams/" + team.teamId() + "/members", admin, null)
                                .expectStatus()
                                .isOk()
                                .expectBody(String.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(body)
                .contains("\"memberId\":" + team.memberId())
                .doesNotContain("\"email\"")
                .contains("\"displayName\":\"" + admin.substring(0, admin.indexOf('@')) + "\"")
                .contains("\"role\":\"ADMIN\"", "\"status\":\"ACTIVE\"", "\"joinedAt\":")
                .doesNotContain("leftAt");
    }

    @Test
    void listingMembersNeedsAnActiveMembership() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.MEMBER);
        leave(emailOf(leaver), team.teamId()).expectStatus().isNoContent();
        final var uri = "/v1/teams/" + team.teamId() + "/members";

        send(HttpMethod.GET, uri, uniqueEmail("stranger"), null).expectStatus().isNotFound();
        send(HttpMethod.GET, uri, emailOf(leaver), null).expectStatus().isNotFound();
        send(HttpMethod.GET, "/v1/teams/999999999/members", admin, null)
                .expectStatus()
                .isNotFound();
    }

    // --- add ----------------------------------------------------------------------------

    @Test
    void anAdminAddsAnExistingUserAsMemberByDefaultIgnoringCaseAndSpaces() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = uniqueEmail("diver");
        me(diver);

        final var member =
                Objects.requireNonNull(
                        send(
                                        HttpMethod.POST,
                                        "/v1/teams/" + team.teamId() + "/members",
                                        admin,
                                        Map.of(
                                                "username",
                                                "  "
                                                        + usernameOf(diver).toUpperCase(Locale.ROOT)
                                                        + "  "))
                                .expectStatus()
                                .isOk()
                                .expectBody(Member.class)
                                .returnResult()
                                .getResponseBody());

        assertThat(member.role()).isEqualTo(Role.MEMBER);
        assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.joinedAt()).isNotNull();
        assertThat(member.leftAt()).isNull();
        assertThat(me(diver).teams())
                .extracting(summary -> summary.teamId())
                .containsExactly(team.teamId());
        assertThat(me(diver).teams().getFirst().memberId()).isEqualTo(member.memberId());
        assertThat(members(admin, team.teamId())).contains(member);
    }

    @Test
    void anAdminCanAddAnotherAdmin() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        final var second = addMember(admin, team.teamId(), uniqueEmail("second"), Role.ADMIN);

        assertThat(second.role()).isEqualTo(Role.ADMIN);
        // The new admin may now manage the team.
        send(HttpMethod.PUT, "/v1/teams/" + team.teamId(), emailOf(second), validTeam())
                .expectStatus()
                .isOk();
    }

    @Test
    void addingAnActiveMemberAgainIsAConflict() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        me(emailOf(diver));

        add(admin, team.teamId(), Map.of("username", usernameOf(emailOf(diver)), "role", "ADMIN"))
                .expectStatus()
                .isEqualTo(409);
        // The creator is an ACTIVE member as well.
        add(admin, team.teamId(), Map.of("username", usernameOf(admin)))
                .expectStatus()
                .isEqualTo(409);
        assertThat(members(admin, team.teamId())).hasSize(2);
        assertThat(members(admin, team.teamId()))
                .filteredOn(member -> member.memberId() == diver.memberId())
                .extracting(Member::role)
                .containsExactly(Role.MEMBER);
    }

    @Test
    void addingALeftMemberReactivatesTheSameRowWithAFreshJoinedAtAndTheRequestedRole() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var first = addMember(admin, team.teamId(), uniqueEmail("rejoiner"), Role.ADMIN);
        leave(emailOf(first), team.teamId()).expectStatus().isNoContent();

        final var again = addMember(admin, team.teamId(), emailOf(first), Role.MEMBER);

        assertThat(again.memberId()).isEqualTo(first.memberId());
        assertThat(again.joinedAt()).isAfter(first.joinedAt());
        assertThat(again.leftAt()).isNull();
        assertThat(again.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(again.role()).isEqualTo(Role.MEMBER);
        assertThat(members(admin, team.teamId())).hasSize(2).contains(again);
        assertThat(me(emailOf(first)).teams()).hasSize(1);
        send(HttpMethod.GET, "/v1/teams/" + team.teamId(), emailOf(first), null)
                .expectStatus()
                .isOk();
    }

    @Test
    void addingMembersNeedsAnAdmin() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.ADMIN);
        leave(emailOf(leaver), team.teamId()).expectStatus().isNoContent();
        final var body = Map.<String, Object>of("username", "newcomer");

        add(emailOf(diver), team.teamId(), body).expectStatus().isForbidden();
        add(uniqueEmail("stranger"), team.teamId(), body).expectStatus().isNotFound();
        add(emailOf(leaver), team.teamId(), body).expectStatus().isNotFound();
        add(admin, 999999999L, body).expectStatus().isNotFound();
        assertThat(members(admin, team.teamId())).hasSize(3);
    }

    @Test
    void invalidAddRequestsAreBadRequests() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        for (final var body :
                List.<Map<String, Object>>of(
                        Map.of("username", "nobody-with-this-name"),
                        Map.of("username", "   "),
                        Map.of("role", "ADMIN"),
                        Map.of("username", usernameOf(admin), "role", "OWNER"))) {
            add(admin, team.teamId(), body).expectStatus().isBadRequest();
        }
        assertThat(members(admin, team.teamId())).hasSize(1);
    }

    // --- update -------------------------------------------------------------------------

    @Test
    void anAdminChangesARoleAndTheOtherFieldsStay() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        me(emailOf(diver));

        final var promoted =
                updateOk(admin, team.teamId(), diver.memberId(), Map.of("role", "ADMIN"));

        assertThat(promoted)
                .isEqualTo(
                        new Member(
                                diver.memberId(),
                                usernameOf(emailOf(diver)),
                                diver.displayName(),
                                Role.ADMIN,
                                MemberStatus.ACTIVE,
                                diver.joinedAt(),
                                null));
        // Promoted members can manage the team.
        send(HttpMethod.PUT, "/v1/teams/" + team.teamId(), emailOf(diver), validTeam())
                .expectStatus()
                .isOk();
        // An empty update changes nothing.
        assertThat(updateOk(admin, team.teamId(), diver.memberId(), Map.of())).isEqualTo(promoted);
    }

    @Test
    void settingAMemberLeftRemovesAccessAndActiveBringsThemBackWithAFreshJoinedAt() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        me(emailOf(diver));

        final var left = updateOk(admin, team.teamId(), diver.memberId(), Map.of("status", "LEFT"));

        assertThat(left.status()).isEqualTo(MemberStatus.LEFT);
        assertThat(left.leftAt()).isNotNull();
        assertThat(left.joinedAt()).isEqualTo(diver.joinedAt());
        send(HttpMethod.GET, "/v1/teams/" + team.teamId(), emailOf(diver), null)
                .expectStatus()
                .isNotFound();
        assertThat(me(emailOf(diver)).teams()).isEmpty();
        // Still listed (their balance stays visible), and setting LEFT again is harmless.
        assertThat(members(admin, team.teamId())).contains(left);
        assertThat(updateOk(admin, team.teamId(), diver.memberId(), Map.of("status", "LEFT")))
                .isEqualTo(left);

        final var back =
                updateOk(admin, team.teamId(), diver.memberId(), Map.of("status", "ACTIVE"));

        assertThat(back.memberId()).isEqualTo(diver.memberId());
        assertThat(back.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(back.leftAt()).isNull();
        assertThat(back.joinedAt()).isAfter(diver.joinedAt());
        assertThat(back.role()).isEqualTo(Role.MEMBER);
        assertThat(me(emailOf(diver)).teams()).hasSize(1);
    }

    @Test
    void roleAndStatusCanChangeInOneRequest() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        me(emailOf(diver));
        updateOk(admin, team.teamId(), diver.memberId(), Map.of("status", "LEFT"));

        final var back =
                updateOk(
                        admin,
                        team.teamId(),
                        diver.memberId(),
                        Map.of("status", "ACTIVE", "role", "ADMIN"));

        assertThat(back.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(back.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void updatingMembersNeedsAnAdminAndAMemberOfThatTeam() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.ADMIN);
        leave(emailOf(leaver), team.teamId()).expectStatus().isNoContent();
        final var otherTeam = createTeam(uniqueEmail("other-admin"));
        final var body = Map.<String, Object>of("role", "ADMIN");

        update(emailOf(diver), team.teamId(), diver.memberId(), body).expectStatus().isForbidden();
        update(uniqueEmail("stranger"), team.teamId(), diver.memberId(), body)
                .expectStatus()
                .isNotFound();
        update(emailOf(leaver), team.teamId(), diver.memberId(), body).expectStatus().isNotFound();
        update(admin, 999999999L, diver.memberId(), body).expectStatus().isNotFound();
        // A member id of another team (or no team at all) is not found in this one.
        update(admin, team.teamId(), otherTeam.memberId(), body).expectStatus().isNotFound();
        update(admin, team.teamId(), 999999999L, body).expectStatus().isNotFound();
        assertThat(members(admin, team.teamId()))
                .filteredOn(member -> member.memberId() == diver.memberId())
                .extracting(Member::role)
                .containsExactly(Role.MEMBER);
    }

    @Test
    void anUnknownRoleOrStatusIsABadRequest() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);

        update(admin, team.teamId(), diver.memberId(), Map.of("role", "OWNER"))
                .expectStatus()
                .isBadRequest();
        update(admin, team.teamId(), diver.memberId(), Map.of("status", "GONE"))
                .expectStatus()
                .isBadRequest();
    }

    // --- leave --------------------------------------------------------------------------

    @Test
    void aMemberLeavesAndLosesAccessButStaysListedAsLeft() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);

        leave(emailOf(diver), team.teamId()).expectStatus().isNoContent();

        send(HttpMethod.GET, "/v1/teams/" + team.teamId(), emailOf(diver), null)
                .expectStatus()
                .isNotFound();
        assertThat(me(emailOf(diver)).teams()).isEmpty();
        final var listed =
                members(admin, team.teamId()).stream()
                        .filter(member -> member.memberId() == diver.memberId())
                        .findFirst()
                        .orElseThrow();
        assertThat(listed.status()).isEqualTo(MemberStatus.LEFT);
        assertThat(listed.leftAt()).isNotNull();
        assertThat(listed.joinedAt()).isEqualTo(diver.joinedAt());
    }

    @Test
    void leavingNeedsAnActiveMembership() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var leaver = addMember(admin, team.teamId(), uniqueEmail("leaver"), Role.MEMBER);
        leave(emailOf(leaver), team.teamId()).expectStatus().isNoContent();

        leave(emailOf(leaver), team.teamId()).expectStatus().isNotFound();
        leave(uniqueEmail("stranger"), team.teamId()).expectStatus().isNotFound();
        leave(admin, 999999999L).expectStatus().isNotFound();
    }

    // --- last-admin guard (DOMAIN §10) ---------------------------------------------------

    @Test
    void theOnlyAdminCannotDemoteThemselvesBeSetLeftOrLeave() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var diver = addMember(admin, team.teamId(), uniqueEmail("diver"), Role.MEMBER);

        assertLastAdmin(update(admin, team.teamId(), team.memberId(), Map.of("role", "MEMBER")));
        assertLastAdmin(update(admin, team.teamId(), team.memberId(), Map.of("status", "LEFT")));
        assertLastAdmin(
                update(
                        admin,
                        team.teamId(),
                        team.memberId(),
                        Map.of("role", "MEMBER", "status", "LEFT")));
        assertLastAdmin(leave(admin, team.teamId()));

        // Nothing changed.
        final var self =
                members(admin, team.teamId()).stream()
                        .filter(member -> member.memberId() == team.memberId())
                        .findFirst()
                        .orElseThrow();
        assertThat(self.role()).isEqualTo(Role.ADMIN);
        assertThat(self.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(me(admin).teams()).hasSize(1);
        // A plain member is no obstacle, and neither is re-saving the admin as admin.
        assertThat(updateOk(admin, team.teamId(), team.memberId(), Map.of("role", "ADMIN")).role())
                .isEqualTo(Role.ADMIN);
        updateOk(admin, team.teamId(), diver.memberId(), Map.of("status", "LEFT"));
    }

    @Test
    void aLeftAdminDoesNotCountAsAnAdmin() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var second = addMember(admin, team.teamId(), uniqueEmail("second"), Role.ADMIN);
        leave(emailOf(second), team.teamId()).expectStatus().isNoContent();

        assertLastAdmin(leave(admin, team.teamId()));
        assertLastAdmin(update(admin, team.teamId(), team.memberId(), Map.of("role", "MEMBER")));
    }

    @Test
    void withASecondAdminTheFirstCanDemoteBeSetLeftOrLeave() {
        for (final var change : List.of("demote", "set-left", "leave")) {
            final var admin = uniqueEmail("admin");
            final var team = createTeam(admin);
            final var second = addMember(admin, team.teamId(), uniqueEmail("second"), Role.ADMIN);
            me(emailOf(second));

            switch (change) {
                case "demote" ->
                        assertThat(
                                        updateOk(
                                                        admin,
                                                        team.teamId(),
                                                        team.memberId(),
                                                        Map.of("role", "MEMBER"))
                                                .role())
                                .isEqualTo(Role.MEMBER);
                case "set-left" ->
                        assertThat(
                                        updateOk(
                                                        emailOf(second),
                                                        team.teamId(),
                                                        team.memberId(),
                                                        Map.of("status", "LEFT"))
                                                .status())
                                .isEqualTo(MemberStatus.LEFT);
                default -> leave(admin, team.teamId()).expectStatus().isNoContent();
            }

            // The remaining admin is now the last one.
            assertLastAdmin(
                    update(
                            emailOf(second),
                            team.teamId(),
                            second.memberId(),
                            Map.of("role", "MEMBER")));
            assertThat(members(emailOf(second), team.teamId()))
                    .filteredOn(
                            member ->
                                    member.role() == Role.ADMIN
                                            && member.status() == MemberStatus.ACTIVE)
                    .extracting(Member::memberId)
                    .containsExactly(second.memberId());
        }
    }

    @Test
    void twoAdminsDemotingEachOtherAtOnceCannotEmptyTheTeamOfAdmins() {
        final var firstEmail = uniqueEmail("first");
        final var team = createTeam(firstEmail);
        final var second = addMember(firstEmail, team.teamId(), uniqueEmail("second"), Role.ADMIN);
        me(emailOf(second));

        final var firstDemotesSecond =
                CompletableFuture.supplyAsync(
                        () -> demoteStatus(firstEmail, team, second.memberId()));
        final var secondDemotesFirst =
                CompletableFuture.supplyAsync(
                        () -> demoteStatus(emailOf(second), team, team.memberId()));
        final var statuses = List.of(firstDemotesSecond.join(), secondDemotesFirst.join());

        assertThat(statuses).containsOnlyOnce(200);
        assertThat(
                        members(firstEmail, team.teamId()).stream()
                                .filter(member -> member.role() == Role.ADMIN)
                                .count())
                .isEqualTo(1);
    }

    // --- helpers ------------------------------------------------------------------------

    private int demoteStatus(final String actor, final Team team, final long targetMemberId) {
        return update(actor, team.teamId(), targetMemberId, Map.of("role", "MEMBER"))
                .returnResult()
                .getStatus()
                .value();
    }

    private static void assertLastAdmin(final ResponseSpec response) {
        response.expectStatus()
                .isEqualTo(409)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains(LAST_ADMIN));
    }

    private static Map<String, Object> validTeam() {
        return new HashMap<>(Map.of("name", "Blenders", "defaultLossPolicy", "POOLED"));
    }

    private ResponseSpec add(
            final String actor, final long teamId, final Map<String, Object> body) {
        return send(HttpMethod.POST, "/v1/teams/" + teamId + "/members", actor, body);
    }

    private ResponseSpec update(
            final String actor,
            final long teamId,
            final long memberId,
            final Map<String, Object> body) {
        return send(HttpMethod.PUT, "/v1/teams/" + teamId + "/members/" + memberId, actor, body);
    }

    private Member updateOk(
            final String actor,
            final long teamId,
            final long memberId,
            final Map<String, Object> body) {
        return Objects.requireNonNull(
                update(actor, teamId, memberId, body)
                        .expectStatus()
                        .isOk()
                        .expectBody(Member.class)
                        .returnResult()
                        .getResponseBody());
    }

    private ResponseSpec leave(final String actor, final long teamId) {
        return send(HttpMethod.POST, "/v1/teams/" + teamId + "/members/me/leave", actor, null);
    }
}

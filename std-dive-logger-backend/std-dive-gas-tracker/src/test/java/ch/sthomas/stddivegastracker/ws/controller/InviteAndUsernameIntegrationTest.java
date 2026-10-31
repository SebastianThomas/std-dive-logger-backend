package ch.sthomas.stddivegastracker.ws.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.model.team.InviteLink;
import ch.sthomas.stddivegastracker.model.team.InvitePreview;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.support.AbstractTeamIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.Objects;

/** DOMAIN §2, API.md §2: finding people by username and by link, never seeing their email. */
final class InviteAndUsernameIntegrationTest extends AbstractTeamIntegrationTest {

    @Test
    void aUsernameFromTheTokenStartsTheDisplayNameAndFindsThePerson() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var email = uniqueEmail("private.person");
        final var username = "Diver" + email.hashCode();

        final var me = signIn(email, username);
        assertThat(me.username()).isEqualTo(username);
        assertThat(me.displayName()).isEqualTo(username);

        final var added = addByUsername(admin, team.teamId(), username.toLowerCase());
        assertThat(added.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(added.username()).isEqualTo(username);
        assertThat(added.displayName()).isEqualTo(username);

        // an @ is what people type by habit; a second add is refused, as with an email
        send(HttpMethod.POST, membersUri(team.teamId()), admin, Map.of("username", "@" + username))
                .expectStatus()
                .isEqualTo(409);
    }

    @Test
    void teammatesNeverSeeAnEmailAddress() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var email = uniqueEmail("private.person");
        final var username = "Quiet" + email.hashCode();
        signIn(email, username);
        addByUsername(admin, team.teamId(), username);
        addMember(admin, team.teamId(), uniqueEmail("not.signed.in"), Role.MEMBER);

        final var body =
                send(HttpMethod.GET, membersUri(team.teamId()), admin, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(String.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(body).contains(username).doesNotContain("@team.test", "\"email\"");
    }

    @Test
    void anUnknownUsernameIsRefusedInsteadOfWaitingForWhoeverRegistersIt() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);

        send(HttpMethod.POST, membersUri(team.teamId()), admin, Map.of("username", "nobody-here"))
                .expectStatus()
                .isBadRequest();

        assertThat(members(admin, team.teamId())).hasSize(1);
    }

    @Test
    void aTokenWithoutAUsernameKeepsTheKnownOne() {
        final var email = uniqueEmail("kept");
        final var username = "Kept" + email.hashCode();
        signIn(email, username);

        assertThat(me(email).username()).isEqualTo(username);
    }

    @Test
    void aNewHolderOfAUsernameTakesItOverFromTheOldAccount() {
        final var firstEmail = uniqueEmail("first");
        final var username = "Reused" + firstEmail.hashCode();
        signIn(firstEmail, username);

        final var second = signIn(uniqueEmail("second"), username);

        assertThat(second.username()).isEqualTo(username);
        assertThat(me(firstEmail).username()).isNull();
    }

    @Test
    void anAdminManagesTheLinkAndAnyoneSignedInCanJoinWithIt() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        assertThat(link(HttpMethod.GET, admin, team.teamId()).token()).isNull();
        final var token = newToken(admin, team.teamId());
        assertThat(token).hasSizeGreaterThanOrEqualTo(40);
        assertThat(link(HttpMethod.GET, admin, team.teamId()).token()).isEqualTo(token);

        final var joiner = uniqueEmail("joiner");
        final var preview = invite(HttpMethod.GET, joiner, token, "");
        assertThat(preview.teamId()).isEqualTo(team.teamId());
        assertThat(preview.teamName()).isEqualTo(team.name());
        assertThat(preview.alreadyMember()).isFalse();
        assertThat(me(joiner).teams()).isEmpty();

        final var joined = invite(HttpMethod.POST, joiner, token, "/join");
        assertThat(joined.alreadyMember()).isTrue();
        assertThat(me(joiner).teams())
                .singleElement()
                .satisfies(
                        summary -> {
                            assertThat(summary.teamId()).isEqualTo(team.teamId());
                            assertThat(summary.role()).isEqualTo(Role.MEMBER);
                        });

        // joining twice changes nothing
        invite(HttpMethod.POST, joiner, token, "/join");
        assertThat(members(admin, team.teamId())).hasSize(2);
    }

    @Test
    void onlyAnAdminSeesOrChangesTheLink() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var memberEmail = uniqueEmail("member");
        me(memberEmail);
        addMember(admin, team.teamId(), memberEmail, Role.MEMBER);
        link(HttpMethod.POST, admin, team.teamId());

        for (final var method :
                new HttpMethod[] {HttpMethod.GET, HttpMethod.POST, HttpMethod.DELETE}) {
            send(method, linkUri(team.teamId()), memberEmail, null).expectStatus().isForbidden();
        }
    }

    @Test
    void aResetOrTurnedOffLinkStopsWorking() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var old = link(HttpMethod.POST, admin, team.teamId()).token();
        final var current = link(HttpMethod.POST, admin, team.teamId()).token();
        assertThat(current).isNotEqualTo(old);
        final var person = uniqueEmail("person");

        send(HttpMethod.POST, "/v1/invites/" + old + "/join", person, null)
                .expectStatus()
                .isNotFound();
        send(HttpMethod.GET, "/v1/invites/" + old, person, null).expectStatus().isNotFound();

        assertThat(link(HttpMethod.DELETE, admin, team.teamId()).token()).isNull();
        send(HttpMethod.POST, "/v1/invites/" + current + "/join", person, null)
                .expectStatus()
                .isNotFound();
        assertThat(me(person).teams()).isEmpty();
    }

    @Test
    void theLinkDoesNotUndoARemoval() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var token = newToken(admin, team.teamId());
        final var person = uniqueEmail("person");
        invite(HttpMethod.POST, person, token, "/join");
        final var member =
                members(admin, team.teamId()).stream()
                        .filter(found -> found.memberId() != team.memberId())
                        .findFirst()
                        .orElseThrow();
        send(
                        HttpMethod.PUT,
                        membersUri(team.teamId()) + "/" + member.memberId(),
                        admin,
                        Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();

        send(HttpMethod.POST, "/v1/invites/" + token + "/join", person, null)
                .expectStatus()
                .isEqualTo(409);
        assertThat(me(person).teams()).isEmpty();
    }

    private MeResponse signIn(final String email, final String username) {
        return meWithToken(
                "Bearer "
                        + TestJwts.sign(
                                TestJwts.builder(TestJwts.uidOf(email), email)
                                        .claim("username", username)));
    }

    private Member addByUsername(final String admin, final long teamId, final String username) {
        return Objects.requireNonNull(
                send(HttpMethod.POST, membersUri(teamId), admin, Map.of("username", username))
                        .expectStatus()
                        .isOk()
                        .expectBody(Member.class)
                        .returnResult()
                        .getResponseBody());
    }

    private String newToken(final String admin, final long teamId) {
        return Objects.requireNonNull(link(HttpMethod.POST, admin, teamId).token());
    }

    private InviteLink link(final HttpMethod method, final String admin, final long teamId) {
        return Objects.requireNonNull(
                send(method, linkUri(teamId), admin, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(InviteLink.class)
                        .returnResult()
                        .getResponseBody());
    }

    private InvitePreview invite(
            final HttpMethod method, final String email, final String token, final String path) {
        return Objects.requireNonNull(
                send(method, "/v1/invites/" + token + path, email, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(InvitePreview.class)
                        .returnResult()
                        .getResponseBody());
    }

    private static String membersUri(final long teamId) {
        return "/v1/teams/" + teamId + "/members";
    }

    private static String linkUri(final long teamId) {
        return "/v1/teams/" + teamId + "/invite-link";
    }
}

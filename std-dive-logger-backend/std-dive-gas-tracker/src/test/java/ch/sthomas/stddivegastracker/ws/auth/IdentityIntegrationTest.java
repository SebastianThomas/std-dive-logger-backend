package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.service.UserService;
import ch.sthomas.stddivegastracker.support.AbstractTeamIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * DOMAIN §2: the identity is the dive-logger {@code uid}, the email a copy refreshed on every
 * request, and an invited email turns into a membership at its first verified sign-in. Tokens come
 * from {@link TestJwts}; a person is known by email ({@link TestJwts#uidOf}) unless a test changes
 * the email of one uid.
 */
final class IdentityIntegrationTest extends AbstractTeamIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserService userService;

    private @Nullable String storedEmail(final long uid) {
        return jdbc.queryForObject(
                "SELECT email FROM t_user WHERE pk_user_id = ?", String.class, uid);
    }

    private int countUsersWithEmail(final String email) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT count(*) FROM t_user WHERE email = ?", Integer.class, email));
    }

    private Member memberOf(final String adminEmail, final long teamId, final long memberId) {
        return members(adminEmail, teamId).stream()
                .filter(member -> member.memberId() == memberId)
                .findFirst()
                .orElseThrow();
    }

    // --- email change ---------------------------------------------------------------------

    @Test
    void aChangedEmailKeepsTheUserTheirMembershipsAndTheirDisplayName() {
        final var oldEmail = uniqueEmail("before");
        final var newEmail = uniqueEmail("after");
        final var uid = TestJwts.uidOf(oldEmail);
        final var team = createTeam(oldEmail);
        setDisplayName(oldEmail, "Ulla");

        final var changed = meWithToken(bearer(uid, newEmail));

        assertThat(changed.userId()).isEqualTo(uid);
        assertThat(changed.email()).isEqualTo(newEmail);
        assertThat(changed.displayName()).isEqualTo("Ulla");
        assertThat(changed.teams()).hasSize(1);
        assertThat(changed.teams().getFirst().teamId()).isEqualTo(team.teamId());
        assertThat(changed.teams().getFirst().memberId()).isEqualTo(team.memberId());
        assertThat(changed.teams().getFirst().role()).isEqualTo(Role.ADMIN);
        sendWithToken(HttpMethod.GET, "/v1/teams/" + team.teamId(), bearer(uid, newEmail), null)
                .expectStatus()
                .isOk();
        assertThat(membersWithToken(bearer(uid, newEmail), team.teamId()))
                .singleElement()
                .satisfies(
                        member -> {
                            assertThat(member.memberId()).isEqualTo(team.memberId());
                            assertThat(member.displayName()).isEqualTo("Ulla");
                        });
        assertThat(storedEmail(uid)).isEqualTo(newEmail);
        assertThat(countUsersWithEmail(oldEmail)).isZero();
        // The old address is free: nobody holds it any more.
        assertThat(countUsersWithEmail(newEmail)).isEqualTo(1);
    }

    @Test
    void anotherUserHoldingTheNewEmailLosesItAndGetsAnEmailAgainOnTheirNextRequest() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var holderEmail = uniqueEmail("holder");
        final var holder = addMember(admin, team.teamId(), holderEmail, Role.MEMBER);
        final var holderUid = TestJwts.uidOf(holderEmail);
        me(holderEmail);
        final var claimerEmail = uniqueEmail("claimer");
        final var claimerUid = TestJwts.uidOf(claimerEmail);
        me(claimerEmail);
        assertThat(holder.status()).isEqualTo(MemberStatus.PENDING);

        // The claimer's dive-logger address becomes the one the holder's copy still shows.
        assertThat(meWithToken(bearer(claimerUid, holderEmail)).email()).isEqualTo(holderEmail);

        assertThat(storedEmail(claimerUid)).isEqualTo(holderEmail);
        assertThat(storedEmail(holderUid)).isNull();
        // The holder keeps the membership.
        assertThat(memberOf(admin, team.teamId(), holder.memberId()))
                .satisfies(
                        member -> {
                            assertThat(member.status()).isEqualTo(MemberStatus.ACTIVE);
                            assertThat(member.displayName()).isEqualTo(holder.displayName());
                        });
        assertThat(
                        send(HttpMethod.GET, "/v1/teams/" + team.teamId() + "/members", admin, null)
                                .expectBody(String.class)
                                .returnResult()
                                .getResponseBody())
                .doesNotContain("\"email\"");

        // The holder's next request carries their current address and sets it again.
        final var holderNewEmail = uniqueEmail("holder-new");
        final var back = meWithToken(bearer(holderUid, holderNewEmail));
        assertThat(back.email()).isEqualTo(holderNewEmail);
        assertThat(back.teams()).hasSize(1);
        assertThat(storedEmail(holderUid)).isEqualTo(holderNewEmail);
        assertThat(storedEmail(claimerUid)).isEqualTo(holderEmail);

        // And the same address can move back: the claimer loses it in turn.
        meWithToken(bearer(holderUid, holderEmail));
        assertThat(storedEmail(holderUid)).isEqualTo(holderEmail);
        assertThat(storedEmail(claimerUid)).isNull();
    }

    @Test
    void aRequestThatChangesNothingWritesNothing() {
        final var email = uniqueEmail("steady");
        final var uid = TestJwts.uidOf(email);
        me(email);
        final var updatedAt = updatedAt(uid);

        me(email);
        me(email);

        assertThat(updatedAt(uid)).isEqualTo(updatedAt);
    }

    private Timestamp updatedAt(final long uid) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        "SELECT updated_at FROM t_user WHERE pk_user_id = ?",
                        Timestamp.class,
                        uid));
    }

    // --- PENDING members are bound at the first verified sign-in ----------------------------

    @Test
    void anInvitedEmailBecomesAnActiveMembershipWithTheSameMemberIdOnTheFirstSignIn() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var person = uniqueEmail("person");
        final var invited = addMember(admin, team.teamId(), person, Role.ADMIN);
        assertThat(invited.status()).isEqualTo(MemberStatus.PENDING);
        send(
                        HttpMethod.POST,
                        "/v1/teams/" + team.teamId() + "/members",
                        admin,
                        Map.of("email", person))
                .expectStatus()
                .isEqualTo(409);
        assertThat(members(admin, team.teamId())).hasSize(2);

        // First sign-in, the address in another case: normalised, so it matches.
        final var me =
                meWithToken(bearer(TestJwts.uidOf(person), "  " + person.toUpperCase(Locale.ROOT)));

        assertThat(me.userId()).isEqualTo(TestJwts.uidOf(person));
        assertThat(me.teams()).hasSize(1);
        assertThat(me.teams().getFirst().teamId()).isEqualTo(team.teamId());
        assertThat(me.teams().getFirst().memberId()).isEqualTo(invited.memberId());
        assertThat(me.teams().getFirst().role()).isEqualTo(Role.ADMIN);
        final var bound = memberOf(admin, team.teamId(), invited.memberId());
        assertThat(bound.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(bound.displayName()).isEqualTo(invited.displayName());
        assertThat(bound.joinedAt()).isEqualTo(invited.joinedAt());
        assertThat(members(admin, team.teamId())).hasSize(2);
        // The membership is a real one: the new admin can administrate.
        send(HttpMethod.GET, "/v1/teams/" + team.teamId(), person, null).expectStatus().isOk();
        send(
                        HttpMethod.PUT,
                        "/v1/teams/" + team.teamId() + "/members/" + team.memberId(),
                        person,
                        Map.of("role", "MEMBER"))
                .expectStatus()
                .isOk();
    }

    @Test
    void oneSignInBindsTheInvitationsOfEveryTeamAndOnlyThoseOfItsOwnEmail() {
        final var adminA = uniqueEmail("admin-a");
        final var adminB = uniqueEmail("admin-b");
        final var teamA = createTeam(adminA);
        final var teamB = createTeam(adminB);
        final var person = uniqueEmail("person");
        final var someoneElse = uniqueEmail("someone-else");
        final var inA = addMember(adminA, teamA.teamId(), person, Role.MEMBER);
        final var inB = addMember(adminB, teamB.teamId(), person, Role.ADMIN);
        final var other = addMember(adminA, teamA.teamId(), someoneElse, Role.MEMBER);

        final var me = me(person);

        assertThat(me.teams())
                .extracting(summary -> summary.memberId())
                .containsExactlyInAnyOrder(inA.memberId(), inB.memberId());
        assertThat(memberOf(adminA, teamA.teamId(), other.memberId()).status())
                .isEqualTo(MemberStatus.PENDING);
    }

    @Test
    void aDuplicateInvitationStaysPendingAndTheUserKeepsTheirExistingMembership() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var otherTeamAdmin = uniqueEmail("other-admin");
        final var otherTeam = createTeam(otherTeamAdmin);
        final var oldEmail = uniqueEmail("old");
        final var newEmail = uniqueEmail("new");
        final var uid = TestJwts.uidOf(oldEmail);
        final var member = addMember(admin, team.teamId(), oldEmail, Role.MEMBER);
        me(oldEmail);
        // The admin only knows the new address: an unknown email, so another (pending) row.
        final var duplicate = addMember(admin, team.teamId(), newEmail, Role.ADMIN);
        final var elsewhere = addMember(otherTeamAdmin, otherTeam.teamId(), newEmail, Role.MEMBER);
        assertThat(members(admin, team.teamId())).hasSize(3);

        final var me = meWithToken(bearer(uid, newEmail));

        // Nothing is deleted or bound in the team where the user already has a row ...
        assertThat(members(admin, team.teamId()))
                .extracting(Member::memberId, Member::status)
                .containsExactlyInAnyOrder(
                        tuple(team.memberId(), MemberStatus.ACTIVE),
                        tuple(member.memberId(), MemberStatus.ACTIVE),
                        tuple(duplicate.memberId(), MemberStatus.PENDING));
        assertThat(memberOf(admin, team.teamId(), member.memberId()))
                .satisfies(m -> assertThat(m.role()).isEqualTo(Role.MEMBER));
        // ... while the invitation of a team without a row is bound as usual.
        assertThat(me.teams())
                .extracting(summary -> summary.memberId())
                .containsExactlyInAnyOrder(member.memberId(), elsewhere.memberId());
        // The admin can clean up the duplicate.
        send(
                        HttpMethod.PUT,
                        "/v1/teams/" + team.teamId() + "/members/" + duplicate.memberId(),
                        admin,
                        Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();
        assertThat(meWithToken(bearer(uid, newEmail)).teams()).hasSize(2);
    }

    @Test
    void aDuplicateInvitationThatOtherRowsReferenceNeverBreaksTheSignIn() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var oldEmail = uniqueEmail("old");
        final var newEmail = uniqueEmail("new");
        final var uid = TestJwts.uidOf(oldEmail);
        final var member = addMember(admin, team.teamId(), oldEmail, Role.MEMBER);
        me(oldEmail);
        final var duplicate = addMember(admin, team.teamId(), newEmail, Role.MEMBER);
        // Money that was charged to the pending duplicate (a payment from it to the admin).
        jdbc.update(
                """
                INSERT INTO t_payment (fk_team_id, fk_from_member_id, fk_to_member_id, amount, paid_at,
                                       fk_created_by_member_id)
                VALUES (?, ?, ?, 5.00, now(), ?)
                """,
                team.teamId(),
                duplicate.memberId(),
                team.memberId(),
                team.memberId());

        for (var attempt = 0; attempt < 3; attempt++) {
            assertThat(meWithToken(bearer(uid, newEmail)).teams())
                    .extracting(summary -> summary.memberId())
                    .containsExactly(member.memberId());
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM t_payment WHERE fk_from_member_id = ?",
                                Integer.class,
                                duplicate.memberId()))
                .isEqualTo(1);
        assertThat(memberOf(admin, team.teamId(), duplicate.memberId()).status())
                .isEqualTo(MemberStatus.PENDING);
        send(
                        HttpMethod.PUT,
                        "/v1/teams/" + team.teamId() + "/members/" + duplicate.memberId(),
                        admin,
                        Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();
        assertThat(meWithToken(bearer(uid, newEmail)).teams()).hasSize(1);
    }

    @Test
    void anUnverifiedEmailNeverBindsAnInvitation() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var person = uniqueEmail("person");
        final var uid = TestJwts.uidOf(person);
        final var invited = addMember(admin, team.teamId(), person, Role.MEMBER);

        // The decoder rejects unverified tokens when required; this is the second line of defence.
        final var user = userService.resolveAuthenticated(uid, person, false, null);

        assertThat(userService.me(user).teams()).isEmpty();
        assertThat(memberOf(admin, team.teamId(), invited.memberId()).status())
                .isEqualTo(MemberStatus.PENDING);

        userService.resolveAuthenticated(uid, person, true, null);

        assertThat(userService.me(user).teams()).hasSize(1);
        assertThat(memberOf(admin, team.teamId(), invited.memberId()).status())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    void aWithdrawnInvitationIsBoundButStaysLeftUntilTheAdminAddsThePersonAgain() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var person = uniqueEmail("person");
        final var invited = addMember(admin, team.teamId(), person, Role.MEMBER);
        send(
                        HttpMethod.PUT,
                        "/v1/teams/" + team.teamId() + "/members/" + invited.memberId(),
                        admin,
                        Map.of("status", "LEFT"))
                .expectStatus()
                .isOk();

        assertThat(me(person).teams()).isEmpty();

        send(HttpMethod.GET, "/v1/teams/" + team.teamId(), person, null)
                .expectStatus()
                .isNotFound();
        assertThat(memberOf(admin, team.teamId(), invited.memberId()).status())
                .isEqualTo(MemberStatus.LEFT);
        // Now a known user: the same row is reactivated (no second row for the person).
        final var again = addMember(admin, team.teamId(), person, Role.MEMBER);
        assertThat(again.memberId()).isEqualTo(invited.memberId());
        assertThat(again.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(members(admin, team.teamId())).hasSize(2);
        assertThat(me(person).teams()).hasSize(1);
    }

    // --- concurrency ----------------------------------------------------------------------

    @Test
    void parallelFirstRequestsOfOneNewUserAllSucceedAndCreateOneUser() {
        final var email = uniqueEmail("racer");

        final var statuses = inParallel(8, () -> statusOfMe(email));

        assertThat(statuses).containsOnly(200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM t_user WHERE pk_user_id = ?",
                                Integer.class,
                                TestJwts.uidOf(email)))
                .isEqualTo(1);
    }

    @Test
    void parallelFirstSignInsOfAnInvitedPersonBindTheMembershipOnce() {
        final var admin = uniqueEmail("admin");
        final var team = createTeam(admin);
        final var person = uniqueEmail("person");
        final var invited = addMember(admin, team.teamId(), person, Role.MEMBER);

        final var statuses = inParallel(8, () -> statusOfMe(person));

        assertThat(statuses).containsOnly(200);
        assertThat(members(admin, team.teamId()))
                .extracting(Member::memberId, Member::status)
                .containsExactlyInAnyOrder(
                        tuple(team.memberId(), MemberStatus.ACTIVE),
                        tuple(invited.memberId(), MemberStatus.ACTIVE));
    }

    private int statusOfMe(final String email) {
        return send(HttpMethod.GET, "/v1/me", email, null).returnResult().getStatus().value();
    }

    private static List<Integer> inParallel(final int threads, final Supplier<Integer> task) {
        try (final var executor = Executors.newFixedThreadPool(threads)) {
            final var futures =
                    IntStream.range(0, threads)
                            .mapToObj(_ -> CompletableFuture.supplyAsync(task, executor))
                            .toList();
            return futures.stream().map(CompletableFuture::join).toList();
        }
    }
}

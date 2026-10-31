package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * DOMAIN §2: the identity is the dive-logger {@code uid}, the email a copy refreshed on every
 * request. Tokens come from {@link TestJwts}; a person is known by email ({@link TestJwts#uidOf})
 * unless a test changes the email of one uid.
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

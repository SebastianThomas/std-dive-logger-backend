package ch.sthomas.stddivegastracker.ws.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.service.UserService;
import ch.sthomas.stddivegastracker.support.AbstractTeamIntegrationTest;
import ch.sthomas.stddivegastracker.support.TestJwts;

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
 * DOMAIN §2: the identity is the dive-logger {@code uid}; the email stays with the dive logger.
 * Tokens come from {@link TestJwts}; a person is known by email ({@link TestJwts#uidOf}).
 */
final class IdentityIntegrationTest extends AbstractTeamIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired UserService userService;

    // --- email change ---------------------------------------------------------------------

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

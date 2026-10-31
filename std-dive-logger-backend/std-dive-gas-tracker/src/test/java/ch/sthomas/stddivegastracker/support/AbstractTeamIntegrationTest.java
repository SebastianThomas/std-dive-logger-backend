package ch.sthomas.stddivegastracker.support;

import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.user.MeResponse;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Teams and members over HTTP, for every test class that needs a team to work in (D2+). */
public abstract class AbstractTeamIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** The API shows teammates no email, so tests remember whom they added under which address. */
    private static final Map<Long, String> EMAILS = new ConcurrentHashMap<>();

    /** The email of a member who was just added or has signed in (never null for those). */
    protected static String emailOf(final Member member) {
        return Objects.requireNonNull(EMAILS.get(member.memberId()));
    }

    /** The username test tokens carry for an address (TestJwts). */
    protected static String usernameOf(final String email) {
        return email.substring(0, email.indexOf('@'));
    }

    /** A fresh address per call: the DB is shared by all test classes and never reset. */
    protected static String uniqueEmail(final String name) {
        return name + SEQUENCE.incrementAndGet() + "@team.test";
    }

    /** Sends a request as the user known by {@code email}; {@code body} is JSON when not null. */
    protected ResponseSpec send(
            final HttpMethod method,
            final String uri,
            final String email,
            final @Nullable Object body) {
        return sendWithToken(method, uri, bearer(email), body);
    }

    /**
     * Sends a request with an explicit {@code Authorization} header value (e.g. {@code bearer}).
     */
    protected ResponseSpec sendWithToken(
            final HttpMethod method,
            final String uri,
            final String authorization,
            final @Nullable Object body) {
        final var request =
                client.method(method).uri(uri).header(HttpHeaders.AUTHORIZATION, authorization);
        if (body == null) {
            return request.exchange();
        }
        return request.contentType(MediaType.APPLICATION_JSON).body(body).exchange();
    }

    protected MeResponse me(final String email) {
        return meWithToken(bearer(email));
    }

    protected MeResponse meWithToken(final String authorization) {
        return Objects.requireNonNull(
                sendWithToken(HttpMethod.GET, "/v1/me", authorization, null)
                        .expectStatus()
                        .isOk()
                        .expectBody(MeResponse.class)
                        .returnResult()
                        .getResponseBody());
    }

    protected void setDisplayName(final String email, final String displayName) {
        send(HttpMethod.PUT, "/v1/me", email, Map.of("displayName", displayName))
                .expectStatus()
                .isOk();
    }

    /** A team with the default settings, created by {@code adminEmail} (who becomes its ADMIN). */
    protected Team createTeam(final String adminEmail) {
        return createTeam(adminEmail, Map.of("name", "Team", "defaultLossPolicy", "TRUE_UP"));
    }

    protected Team createTeam(final String adminEmail, final Map<String, Object> body) {
        return Objects.requireNonNull(
                send(HttpMethod.POST, "/v1/teams", adminEmail, body)
                        .expectStatus()
                        .isOk()
                        .expectBody(Team.class)
                        .returnResult()
                        .getResponseBody());
    }

    /** Adds {@code email} to the team as {@code adminEmail}, expecting success. */
    protected Member addMember(
            final String adminEmail, final long teamId, final String email, final Role role) {
        me(email);
        final var member =
                Objects.requireNonNull(
                        send(
                                        HttpMethod.POST,
                                        "/v1/teams/" + teamId + "/members",
                                        adminEmail,
                                        Map.of("username", usernameOf(email), "role", role))
                                .expectStatus()
                                .isOk()
                                .expectBody(Member.class)
                                .returnResult()
                                .getResponseBody());
        EMAILS.put(member.memberId(), email);
        return member;
    }

    protected List<Member> members(final String email, final long teamId) {
        return membersWithToken(bearer(email), teamId);
    }

    protected List<Member> membersWithToken(final String authorization, final long teamId) {
        return Objects.requireNonNull(
                sendWithToken(
                                HttpMethod.GET,
                                "/v1/teams/" + teamId + "/members",
                                authorization,
                                null)
                        .expectStatus()
                        .isOk()
                        .expectBody(new ParameterizedTypeReference<List<Member>>() {})
                        .returnResult()
                        .getResponseBody());
    }
}

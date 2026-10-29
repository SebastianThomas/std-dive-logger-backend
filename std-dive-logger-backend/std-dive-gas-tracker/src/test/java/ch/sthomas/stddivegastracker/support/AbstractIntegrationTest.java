package ch.sthomas.stddivegastracker.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base for every Spring integration test: one Postgres container per JVM (singleton pattern, so the
 * cached Spring context never outlives its DB) and one shared context. Don't add
 * per-class @MockitoBean/@TestPropertySource without need - each variant is a new context.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=test",
            "spring.docker.compose.enabled=false",
            "ch.sthomas.stddivegastracker.auth.require-email-verified-claim=true",
            "ch.sthomas.stddivegastracker.auth.dev-login-enabled=false",
        })
@AutoConfigureRestTestClient
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).withReuse(true);

    static {
        POSTGRES.start();
    }

    @Autowired protected RestTestClient client;

    /**
     * The TEST-ONLY public key (a PEM, as a deployed config would hold it), the one signing key.
     */
    @DynamicPropertySource
    static void authProperties(final DynamicPropertyRegistry registry) {
        registry.add(
                "ch.sthomas.stddivegastracker.auth.public-keys", () -> TestJwts.PUBLIC_KEY_PEM);
    }

    /** The token of the user a test knows only by email ({@link TestJwts#uidOf}). */
    protected static String bearer(final String email) {
        return "Bearer " + TestJwts.accessToken(email);
    }

    protected static String bearer(final long uid, final String email) {
        return "Bearer " + TestJwts.accessToken(uid, email);
    }
}

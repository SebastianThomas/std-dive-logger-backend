package ch.sthomas.stddivelogger.autocomplete;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real end-to-end HTTP test for the {@code autocomplete} app - this app previously had no startup
 * or HTTP-level test coverage at all, which is exactly the gap that let the storage-service
 * consolidation regression (fixed in v0.4.2, see {@code AGENTS.md}'s Spring DI pitfall section)
 * ship unnoticed until someone ran {@code mvn spring-boot:run} by hand. A plain context-load smoke
 * test would already catch a startup-time DI failure like that one; this goes one step further and
 * also asserts each endpoint actually responds, since (unlike {@code analytics}, which is
 * scheduled-jobs-only with no controllers) this app's whole purpose is serving HTTP requests.
 *
 * <p>No JWT/auth setup needed - {@code autocomplete}'s default active profile is just {@code
 * no-security} (see its {@code application.properties}), and none of these endpoints require a
 * logged-in user (the controller's own comment notes {@code /tag} explicitly falls back to
 * system-wide tags when there's no authenticated principal).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Testcontainers
class AutocompleteControllerIntegrationTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                            DockerImageName.parse("postgis/postgis:18-3.6")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withReuse(true);

    @Autowired private RestTestClient restTestClient;

    @Test
    void siteAutocompleteRespondsWithAnEmptyPageOnAnEmptyDatabase() {
        restTestClient
                .get()
                .uri("/v1/autocomplete/site?query=wreck")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"result\":[]"));
    }

    @Test
    void userAutocompleteRespondsWithAnEmptyPageOnAnEmptyDatabase() {
        restTestClient
                .get()
                .uri("/v1/autocomplete/user?query=diver")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("\"result\":[]"));
    }

    @Test
    void groupAutocompleteRespondsWithAnEmptyListOnAnEmptyDatabase() {
        restTestClient
                .get()
                .uri("/v1/autocomplete/group?query=club")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).isEqualTo("[]"));
    }

    @Test
    void tagAutocompleteFallsBackToSystemTagsForAnAnonymousCaller() {
        restTestClient
                .get()
                .uri("/v1/autocomplete/tag?query=wreck")
                .exchange()
                .expectStatus()
                .isOk();
    }

    @Test
    void aBlankQueryIsRejectedAsABadRequest() {
        restTestClient
                .get()
                .uri("/v1/autocomplete/site?query=")
                .exchange()
                .expectStatus()
                .isBadRequest();
    }
}

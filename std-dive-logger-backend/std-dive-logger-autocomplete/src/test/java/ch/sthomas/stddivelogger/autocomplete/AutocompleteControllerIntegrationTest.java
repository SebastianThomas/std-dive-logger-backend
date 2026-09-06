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
@org.junit.jupiter.api.Tag("slow")
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

    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ch.sthomas.stddivelogger.autocomplete.services.AutocompleteQueries queries;

    @Test
    void autocompleteDoesNotBootTheWriteServiceOrHibernate() {
        assertThat(context.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class))
                .isEmpty();
        assertThat(context.getBeanNamesForType(ch.sthomas.stddivelogger.service.DiveService.class))
                .isEmpty();
    }

    @Test
    void jdbcSearchPreservesPaginationCoordinatesAndPrivateTagIsolation() {
        final long userId =
                java.util.Objects.requireNonNull(
                        jdbc.queryForObject(
                                "INSERT INTO t_users(email, password, name, verified, custom_icon_url, created_at, updated_at) VALUES ('autocomplete-fixture@example.test', 'unused', 'AutocompleteFixture00', true, 'icon', now(), now()) RETURNING pk_user_id",
                                Long.class));
        try {
            for (int i = 1; i < 11; i++)
                jdbc.update(
                        "INSERT INTO t_users(email, password, name, verified, created_at, updated_at) VALUES (?, 'unused', ?, true, now(), now())",
                        "autocomplete-fixture" + i + "@example.test",
                        "AutocompleteFixture" + String.format("%02d", i));
            jdbc.update("INSERT INTO t_groups(group_name) VALUES ('AutocompleteFixtureClub')");
            jdbc.update(
                    "INSERT INTO t_dive_site(name, location, water_type, zone_id) VALUES ('AutocompleteFixtureSite', ST_SetSRID(ST_MakePoint(8.5, 47.3), 4326), 'FRESH', 'Europe/Zurich')");
            jdbc.update(
                    "INSERT INTO t_tag_definitions(name, fk_user_id) VALUES ('AutocompleteFixturePrivate', ?)",
                    userId);
            jdbc.update("INSERT INTO t_tag_definitions(name) VALUES ('AutocompleteFixturePublic')");
            final var first = queries.users("AutocompleteFixture", 0);
            assertThat(first.totalElements()).isEqualTo(11);
            assertThat(first.totalPages()).isEqualTo(2);
            assertThat(first.result()).hasSize(10);
            assertThat(queries.users("AutocompleteFixture", 1).result()).hasSize(1);
            assertThat(queries.groups("AutocompleteFixture", 0)).hasSize(1);
            final var site = queries.sites("AutocompleteFixture", 0).result().getFirst();
            assertThat(site.latitude()).isEqualTo(47.3);
            assertThat(site.longitude()).isEqualTo(8.5);
            assertThat(site.zoneId()).isEqualTo("Europe/Zurich");
            assertThat(site.waterType())
                    .isEqualTo(ch.sthomas.stddivelogger.model.dive.conditions.WaterType.FRESH);
            assertThat(queries.tags("AutocompleteFixture", null))
                    .extracting(ch.sthomas.stddivelogger.model.dive.TagDefinition::name)
                    .containsExactly("AutocompleteFixturePublic");
            assertThat(queries.tags("AutocompleteFixture", userId)).hasSize(2);
            assertThat(queries.users("' OR 1=1 --", 0).result()).isEmpty();
        } finally {
            jdbc.update("DELETE FROM t_tag_definitions WHERE name LIKE 'AutocompleteFixture%'");
            jdbc.update("DELETE FROM t_dive_site WHERE name LIKE 'AutocompleteFixture%'");
            jdbc.update("DELETE FROM t_groups WHERE group_name LIKE 'AutocompleteFixture%'");
            jdbc.update("DELETE FROM t_users WHERE name LIKE 'AutocompleteFixture%'");
        }
    }
}

package ch.sthomas.stddivegastracker.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.support.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The OpenAPI document is the frontend's compile-time contract (API.md, task D8): it is committed
 * as {@code src/test/resources/openapi.json} and the frontend generates its types from it. A change
 * fails here until it is regenerated with {@code -Dopenapi.update=true}.
 */
final class OpenApiSnapshotTest extends AbstractIntegrationTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/openapi.json");
    private static final Path ACTUAL = Path.of("target/openapi.json");

    private static final JsonMapper JSON =
            JsonMapper.builder()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();

    @Test
    void theApiDocumentMatchesTheCommittedSnapshot() throws IOException {
        final var document =
                Objects.requireNonNull(
                        client.get()
                                .uri("/docs")
                                .exchange()
                                .expectStatus()
                                .isOk()
                                .expectBody(String.class)
                                .returnResult()
                                .getResponseBody());
        final var normalised = normalise(document);
        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, normalised);
        if (Boolean.getBoolean("openapi.update")) {
            Files.writeString(SNAPSHOT, normalised);
        }

        assertThat(normalised)
                .as(
                        "The API changed: run `mvn -pl std-dive-gas-tracker test"
                                + " -Dtest=OpenApiSnapshotTest -Dopenapi.update=true`, then"
                                + " `vue/scripts/gen-api.sh` in the frontend")
                .isEqualTo(Files.readString(SNAPSHOT));
    }

    /** Keys sorted, the random test-server URL dropped: a stable, reviewable file. */
    private static String normalise(final String document) {
        final var tree = JSON.readValue(document, new TypeReference<Map<String, Object>>() {});
        tree.remove("servers");
        return JSON.writeValueAsString(tree) + System.lineSeparator();
    }
}

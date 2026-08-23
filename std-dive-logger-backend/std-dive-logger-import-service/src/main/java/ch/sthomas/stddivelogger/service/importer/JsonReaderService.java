package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.suunto.SuuntoJsonReaderService;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * Dispatches a ".json" upload by content, not by assuming a brand. Add a format: give its reader a
 * {@code matches(JsonNode)} check and add a branch below.
 */
@Service
public class JsonReaderService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final SuuntoJsonReaderService suuntoJsonReaderService;

    public JsonReaderService(final SuuntoJsonReaderService suuntoJsonReaderService) {
        this.suuntoJsonReaderService = suuntoJsonReaderService;
    }

    public ParsedImport parse(final User user, final String filename, final InputStream inputStream)
            throws IOException {
        final var bytes = inputStream.readAllBytes();
        final JsonNode root;
        try {
            root = JSON_MAPPER.readTree(bytes);
        } catch (final JacksonException e) {
            throw unrecognized(filename, e);
        }
        if (SuuntoJsonReaderService.matches(root)) {
            return suuntoJsonReaderService.parse(user, filename, bytes);
        }
        throw unrecognized(filename, null);
    }

    // Deliberately generic - the caller doesn't need to know which field/shape check failed, and
    // saying so would leak internals for no benefit. Full cause is still attached for the logs.
    private static IllegalArgumentException unrecognized(
            final String filename, final @Nullable Throwable cause) {
        final var message = "Could not recognize " + filename + " as a supported dive log export.";
        return cause == null
                ? new IllegalArgumentException(message)
                : new IllegalArgumentException(message, cause);
    }
}

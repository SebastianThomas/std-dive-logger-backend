package ch.sthomas.stddivelogger.service.importer.divesoft;

import ch.sthomas.stddivelogger.model.controller.dive.DivesoftConfigResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Fetches the (non-secret) Auth0 app config wetnotes.com's own frontend uses to sign in, straight
 * from wetnotes.com's public page and Auth0 tenant. This never touches a user's actual wetnotes.com
 * credentials - it only resolves the shared, non-user-specific client app configuration, which we
 * re-fetch (with a short cache) rather than hardcode so a future rotation on their end doesn't
 * silently break this importer.
 *
 * <p>The values live in a {@code window.__REACT_SETTINGS = {...}} block on wetnotes.com's
 * homepage. That block is a plain JS object literal, not JSON (unquoted keys, and some fields are
 * string concatenations like {@code baseUrl + "/callback"}), so it can't be parsed as a whole -
 * instead each field we need is pulled out individually via its own `key: "literal value"` regex.
 * The Auth0 {@code client.js} endpoint used to resolve the login "realm", by contrast, really is
 * JSON and is parsed as such.
 */
@Service
public class DivesoftConfigService {
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("domain:\\s*\"([^\"]*)\"");
    private static final Pattern CLIENT_ID_PATTERN = Pattern.compile("clientId:\\s*\"([^\"]*)\"");
    private static final Pattern CLIENT_SECRET_PATTERN =
            Pattern.compile("clientSecret:\\s*\"([^\"]*)\"");
    private static final Pattern AUDIENCE_PATTERN = Pattern.compile("apiUrl:\\s*\"([^\"]*)\"");
    private static final Pattern API_HOST_URL_PATTERN =
            Pattern.compile("var\\s+apiHostUrl\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CLIENT_JS_PATTERN =
            Pattern.compile("Auth0\\.setClient\\((\\{.*})\\);", Pattern.DOTALL);
    // Not served by any endpoint - it's a literal baked into wetnotes.com's own compiled bundle.
    private static final String SCOPE = "openid profile email all:access";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper;
    private volatile CachedConfig cached;

    public DivesoftConfigService(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DivesoftConfigResponse getConfig() {
        final var current = cached;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current.config();
        }
        final var fetched = fetchConfig();
        cached = new CachedConfig(fetched, Instant.now().plus(CACHE_TTL));
        return fetched;
    }

    private DivesoftConfigResponse fetchConfig() {
        final var html = restClient.get().uri("https://wetnotes.com/").retrieve().body(String.class);
        final var domain = extract(DOMAIN_PATTERN, html, "domain");
        final var clientId = extract(CLIENT_ID_PATTERN, html, "clientId");
        final var clientSecret = extract(CLIENT_SECRET_PATTERN, html, "clientSecret");
        final var audience = extract(AUDIENCE_PATTERN, html, "apiUrl");
        final var apiHostUrl = extract(API_HOST_URL_PATTERN, html, "apiHostUrl");

        final var realm = fetchRealm(domain, clientId);

        return new DivesoftConfigResponse(
                domain, clientId, clientSecret, audience, realm, SCOPE, apiHostUrl + "/api/");
    }

    private String fetchRealm(final String domain, final String clientId) {
        final var clientJs =
                restClient
                        .get()
                        .uri("https://{domain}/client/{clientId}.js", domain, clientId)
                        .retrieve()
                        .body(String.class);
        final var matcher = CLIENT_JS_PATTERN.matcher(clientJs);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not find Auth0.setClient(...) on the Auth0 client config endpoint");
        }
        final var clientConfig = readTree(matcher.group(1));
        for (final var strategy : clientConfig.get("strategies")) {
            if ("auth0".equals(strategy.get("name").asText())) {
                return strategy.get("connections").get(0).get("name").asText();
            }
        }
        throw new IllegalStateException(
                "Could not find an 'auth0' strategy in the Auth0 client config");
    }

    private static String extract(final Pattern pattern, final String html, final String fieldName) {
        final var matcher = pattern.matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    "Could not find '" + fieldName + "' in wetnotes.com's homepage settings");
        }
        return matcher.group(1);
    }

    private JsonNode readTree(final String json) {
        try {
            return objectMapper.readTree(json);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not parse Auth0 client config JSON", e);
        }
    }

    private record CachedConfig(DivesoftConfigResponse config, Instant expiresAt) {}
}

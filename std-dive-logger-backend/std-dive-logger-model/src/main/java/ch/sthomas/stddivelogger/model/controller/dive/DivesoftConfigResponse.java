package ch.sthomas.stddivelogger.model.controller.dive;

/**
 * The (non-secret) Auth0 app config wetnotes.com's own frontend uses to sign in - reverse
 * engineered from wetnotes.com's public page and Auth0 tenant, and re-fetched periodically here
 * rather than hardcoded so a future rotation on their end doesn't silently break this importer.
 */
public record DivesoftConfigResponse(
        String domain,
        String clientId,
        String clientSecret,
        String audience,
        String realm,
        String scope,
        String apiBaseUrl) {}

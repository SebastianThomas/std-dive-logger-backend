package ch.sthomas.stddivelogger.model.controller;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Request body for {@code PUT /v1/dives/{id}/tags}.
 *
 * @param manualTagIds IDs of tags the user has explicitly selected (manual).
 * @param dismissedAutoTagIds IDs of auto-detected tags the user has explicitly dismissed. A
 *     dismissed tag will not be re-added automatically until the user re-adds it manually (which
 *     also clears the dismissed flag).
 */
public record UpdateTagsBody(
        @NotNull List<Long> manualTagIds, @NotNull List<Long> dismissedAutoTagIds) {}

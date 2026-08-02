package ch.sthomas.stddivelogger.model.controller;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Request body for {@code POST /v1/dives/{id}/profiles/{profileId}/trim}.
 *
 * @param trimStart everything strictly before this is permanently deleted from the profile. {@code
 *     null} to leave the start of the profile untouched.
 * @param trimEnd everything strictly after this is permanently deleted from the profile. {@code
 *     null} to leave the end of the profile untouched.
 */
public record TrimProfileBody(@Nullable Instant trimStart, @Nullable Instant trimEnd) {}

package ch.sthomas.stddivelogger.model.controller.dive;

import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.geometry.Location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Overrides applied when committing a staged import. When {@code linkToExistingDiveId} is set,
 * the parsed profile(s) are attached to that existing dive instead of creating a new one, and the
 * site/identity fields below are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PendingImportCommitRequest(
        @Nullable @Positive Integer diveNumber,
        @Nullable String diveIdentifier,
        @Nullable String notes,
        @Nullable Visibility visibility,
        @Nullable List<String> namedBuddies,
        @Nullable @Positive Long diveSiteId,
        @Nullable String newSiteName,
        @Nullable Location newSiteLocation,
        @Nullable @Positive Long linkToExistingDiveId,
        /**
         * Applies a trim to one or more profiles (by their index in {@code
         * PendingImportPayload#profiles()}, the same index the preview endpoint reports each
         * profile under) before the dive is created/attached - the pre-commit equivalent of {@code
         * POST /dives/{id}/profiles/{profileId}/trim} for an already-saved dive.
         */
        @Nullable @Valid List<ProfileTrim> profileTrims) {

    public PendingImportCommitRequest {
        if (linkToExistingDiveId != null
                && (diveSiteId != null || newSiteName != null || newSiteLocation != null)) {
            throw new IllegalArgumentException(
                    "linkToExistingDiveId cannot be combined with a dive site override");
        }
    }

    public record ProfileTrim(
            @PositiveOrZero int profileIndex,
            @Nullable Instant trimStart,
            @Nullable Instant trimEnd) {}
}

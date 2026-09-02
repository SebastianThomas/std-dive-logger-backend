package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.user.FrontendUser;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record SimplifiedDive(
        long id,
        FrontendUser user,
        int number,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable Visibility visibility,
        @Nullable DiveSite site,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies,
        @NotNull DiveSummary summary,
        @NotNull List<TagDefinition> tags,
        /** Diver-set "star" - see {@code DiveEntity.highlighted}. */
        boolean highlighted,
        /**
         * A manually-logged dive (no dive-computer file) - the list shows no synthetic-profile
         * preview image for it. See {@code DiveEntity.isManualEntryDive}.
         */
        boolean manualEntry) {}

package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.user.FrontendUser;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record Dive(
        long id,
        FrontendUser user,
        int number,
        String notes,
        @NotNull String customIdentifier,
        @Nullable String previewImage,
        @Nullable Visibility visibility,
        @Nullable DiveGasConsumption gasConsumption,
        /**
         * Real gas consumption computed from tracked per-dive cylinders, not the (mostly
         * unavailable/zero) whole-dive {@code gasConsumption} above - see {@link
         * ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionCalculator}. Null only when
         * this dive has no cylinders tracked at all, not when a figure happens to be zero.
         */
        @Nullable CylinderConsumptionResult cylinderConsumption,
        @Nullable DiveConfiguration configuration,
        @Nullable DiveSite site,
        @NotNull List<DiveProfile> profiles,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies,
        @NotNull DiveSummary summary,
        @NotNull List<TagDefinition> tags) {
    @Override
    @NotNull
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("number", number)
                .append("customIdentifier", customIdentifier)
                .append("previewImage", previewImage)
                .append("site", site)
                .append("profiles", profiles)
                .toString();
    }
}

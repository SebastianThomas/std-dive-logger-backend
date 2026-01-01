package ch.sthomas.stddivelogger.model.dive;

import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.user.FrontendUser;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

public record Dive(
        long id,
        FrontendUser user,
        int number,
        String notes,
        @Nullable String customIdentifier,
        @Nullable String previewImage,
        @Nullable Visibility visibility,
        @Nullable DiveGasConsumption gasConsumption,
        @Nullable DiveConfiguration configuration,
        @Nullable DiveSite site,
        @NotNull List<DiveProfile> profiles,
        @NotNull List<BuddyDive> buddiesDives,
        @NotNull List<String> namedBuddies,
        @NotNull DiveSummary summary) {
    @Override
    @NotNull
    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(number)
                .append(customIdentifier)
                .append(previewImage)
                .append(site)
                .toString();
    }
}

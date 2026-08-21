package ch.sthomas.stddivelogger.model.controller;

import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.dive.conditions.Current;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record UpdateDiveBody(
        @Positive long id,
        @PositiveOrZero int number,
        @Nullable String notes,
        @PositiveOrZero long suitId,
        @Nullable DiveConfiguration configuration,
        @Nullable DiveGasConsumption gasConsumption,
        @Nullable Visibility visibility,
        @Nullable String customIdentifier,
        @Nullable @Positive Long siteId,
        @Nullable List<NamedBuddyInput> namedBuddies,
        @Nullable WaterType waterType,
        @Nullable Current current,
        // At most one of these two may be set (checked below) - both null means the dive's own
        // owner led. See DiveLeader's own doc comment for what each represents.
        @Nullable @Positive Long leaderNamedBuddyId,
        @Nullable @Positive Long leaderBuddyDiveId,
        @Nullable TeamTerminology teamTerminology) {

    public UpdateDiveBody {
        if (leaderNamedBuddyId != null && leaderBuddyDiveId != null) {
            throw new IllegalArgumentException(
                    "A dive leader can be a named buddy or a linked buddy dive, not both.");
        }
    }

    @Override
    @NotNull
    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append(number)
                .append(customIdentifier)
                .toString();
    }
}

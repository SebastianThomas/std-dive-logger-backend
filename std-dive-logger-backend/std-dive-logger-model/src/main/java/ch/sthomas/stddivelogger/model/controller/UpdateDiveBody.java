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
        // At most one of these two may be set (checked below). Both null does NOT by itself mean
        // the owner led - see leaderSelfExplicit below and DiveLeader's own doc comment.
        @Nullable @Positive Long leaderNamedBuddyId,
        @Nullable @Positive Long leaderBuddyDiveId,
        // True only when the owner explicitly picked "Me" in the leader picker - distinguishes
        // that from "never touched the leader picker at all" (both this and the two ids above
        // left at their defaults), which must resolve to DiveLeader.UNSET, not DiveLeader.SELF.
        boolean leaderSelfExplicit,
        @Nullable TeamTerminology teamTerminology) {

    public UpdateDiveBody {
        if (leaderNamedBuddyId != null && leaderBuddyDiveId != null) {
            throw new IllegalArgumentException(
                    "A dive leader can be a named buddy or a linked buddy dive, not both.");
        }
        if (leaderSelfExplicit && (leaderNamedBuddyId != null || leaderBuddyDiveId != null)) {
            throw new IllegalArgumentException(
                    "leaderSelfExplicit cannot be set together with a named/linked dive leader.");
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

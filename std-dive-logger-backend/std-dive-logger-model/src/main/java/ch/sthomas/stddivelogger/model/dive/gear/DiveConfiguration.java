package ch.sthomas.stddivelogger.model.dive.gear;

import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record DiveConfiguration(
        Suit suit,
        BaseConfiguration base,
        @Nullable Double weight,
        @Nullable WeightFeeling weightFeeling,
        List<DiveConfigurationCylinder> cylinders,
        @Nullable CcrUnit ccrUnit,
        // A suit type noted for this dive with no specific saved Suit behind it at all - for
        // gear the diver doesn't own and doesn't want cluttering their permanent "My Suits" list
        // (e.g. a one-off rental). Independent of `suit` - the two are meant to be mutually
        // exclusive in the UI (picking one clears the other) but nothing stops both being set.
        @Nullable SuitType adHocSuitType) {
    public static DiveConfiguration createEmpty(final User user) {
        return new DiveConfiguration(
                Suit.createUnknown(user),
                BaseConfiguration.OTHER,
                null,
                null,
                List.of(),
                null,
                null);
    }
}

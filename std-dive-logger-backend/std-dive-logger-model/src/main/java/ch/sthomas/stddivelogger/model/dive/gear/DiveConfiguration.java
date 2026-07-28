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
        @Nullable CcrUnit ccrUnit) {
    public static DiveConfiguration createEmpty(final User user) {
        return new DiveConfiguration(
                Suit.createUnknown(user), BaseConfiguration.OTHER, null, null, List.of(), null);
    }
}

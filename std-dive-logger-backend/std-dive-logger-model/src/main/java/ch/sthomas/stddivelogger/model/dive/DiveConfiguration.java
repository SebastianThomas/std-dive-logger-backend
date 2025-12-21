package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

import java.util.List;

public record DiveConfiguration(
        Suit suit,
        BaseConfiguration base,
        double weight,
        @Nullable WeightFeeling weightFeeling,
        List<DiveConfigurationCylinder> cylinders) {
    public static final DiveConfiguration EMPTY =
            new DiveConfiguration(null, BaseConfiguration.OTHER, 0, null, List.of());
}

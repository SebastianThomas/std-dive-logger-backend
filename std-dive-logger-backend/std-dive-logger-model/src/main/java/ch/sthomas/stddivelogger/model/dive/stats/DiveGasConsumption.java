package ch.sthomas.stddivelogger.model.dive.stats;

import jakarta.annotation.Nullable;

public record DiveGasConsumption(double sacBar, double rmvLiters, double totalLiters) {
    public static DiveGasConsumption EMPTY = new DiveGasConsumption(0, 0, 0);
}

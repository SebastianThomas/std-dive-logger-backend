package ch.sthomas.stddivelogger.model.dive;

import jakarta.annotation.Nullable;

public record DiveGasConsumption(
        @Nullable Double sacBar, @Nullable Double rmvLiters, @Nullable Double totalLiters) {
    public static DiveGasConsumption EMPTY = new DiveGasConsumption(null, null, null);
}

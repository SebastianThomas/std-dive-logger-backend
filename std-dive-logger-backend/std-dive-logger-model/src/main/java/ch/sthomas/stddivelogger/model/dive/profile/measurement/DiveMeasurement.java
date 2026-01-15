package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveMeasurement(
        Instant time,
        Temperature temperature,
        double depth,
        @Nullable Duration ndl,
        List<DecoStop> deco,
        @Nullable Gas gas,
        @Nullable PO2 po2,
        @Nullable Double rmvLiters,
        @Nullable Double n2,
        @Nullable Double o2Tox,
        @Nullable Double cns) {
    public enum DiveMeasurementProperty {
        TEMPERATURE,
        DEPTH,
        NDL,
        GAS_O2,
        GAS_N2,
        GAS_HE;
    }
}

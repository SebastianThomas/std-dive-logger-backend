package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiveMeasurement(
        Instant time,
        @Nullable Temperature temperature,
        double depth,
        @Nullable Duration ndl,
        @Nullable List<DecoStop> deco,
        @Nullable Gas gas,
        @Nullable PO2 po2,
        @Nullable Double rmvLiters,
        @Nullable Double n2,
        @Nullable Double o2Tox,
        @Nullable Double cns,
        @Nullable DiveMode mode,
        // Time needed for a safe ascent from here (device-assumed ascent rate, e.g. 9-10m/min) -
        // not the same thing as being in mandatory deco: a plain ascent from 3m still reads as
        // ~20s-1min of this, with an empty deco list.
        @Nullable Duration timeToSurface) {
    public enum DiveMeasurementProperty {
        TEMPERATURE,
        DEPTH,
        NDL,
        GAS_O2,
        GAS_N2,
        GAS_HE;
    }
}

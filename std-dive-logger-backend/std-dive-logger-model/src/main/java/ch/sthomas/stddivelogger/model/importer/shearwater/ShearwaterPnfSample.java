package ch.sthomas.stddivelogger.model.importer.shearwater;

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * One decoded sample record out of a Shearwater "Petrel Native Format" (PNF) binary log - the
 * on-device log Shearwater Cloud keeps in {@code log_data.data_bytes_1} (see {@code
 * ShearwaterPnfParser}). Only the fields this project actually models are carried; see the parser's
 * doc comment for what is deliberately left undecoded.
 *
 * <p>{@code decoStopDepthMeters} is 0 when the sample is not in mandatory deco, in which case
 * {@code stopOrNdlTime} is the remaining no-decompression time; when it is > 0 the same source byte
 * is the remaining stop time instead (that dual meaning is the device's own, not a simplification
 * here).
 */
public record ShearwaterPnfSample(
        Duration time,
        double depthMeters,
        double temperatureCelsius,
        double decoStopDepthMeters,
        Duration stopOrNdlTime,
        Duration timeToSurface,
        int o2Percent,
        int heliumPercent,
        @Nullable Double averagePpo2,
        @Nullable Double setpoint,
        @Nullable Double cns,
        boolean closedCircuit) {

    public boolean inMandatoryDeco() {
        return decoStopDepthMeters > 0;
    }
}

package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One {@code <diveLogRecord>} - currentTime is milliseconds elapsed; depths/stop depths are meters;
 * ttsMins/currentNdl/firstStopTime are whole minutes (currentNdl caps at 99, the standard
 * dive-computer "no meaningful limit" display value). Tank pressure/SAC/gas-time fields aren't
 * modeled here - this device had no AI transmitter paired, so those arrive as non-numeric sentinel
 * strings ("AI is off", "N/A", "Not paired") rather than real values in every sample seen so far.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShearwaterDiveLogRecord(
        long currentTime,
        double currentDepth,
        double waterTemp,
        int currentNdl,
        int ttsMins,
        double firstStopDepth,
        double firstStopTime,
        double fractionO2,
        double fractionHe,
        double averagePPO2,
        String currentCircuitSetting,
        double sensor1Millivolts,
        double sensor2Millivolts,
        double sensor3Millivolts) {
    /** Compatibility constructor for synthetic records that do not model cell telemetry. */
    public ShearwaterDiveLogRecord(
            final long currentTime,
            final double currentDepth,
            final double waterTemp,
            final int currentNdl,
            final int ttsMins,
            final double firstStopDepth,
            final double firstStopTime,
            final double fractionO2,
            final double fractionHe,
            final double averagePPO2,
            final String currentCircuitSetting) {
        this(
                currentTime,
                currentDepth,
                waterTemp,
                currentNdl,
                ttsMins,
                firstStopDepth,
                firstStopTime,
                fractionO2,
                fractionHe,
                averagePPO2,
                currentCircuitSetting,
                0,
                0,
                0);
    }

    public boolean hasOxygenCellTelemetry() {
        return sensor1Millivolts > 0 || sensor2Millivolts > 0 || sensor3Millivolts > 0;
    }
}

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
        String currentCircuitSetting) {}

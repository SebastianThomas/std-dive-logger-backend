package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

/**
 * Shearwater Cloud's own roll-up of a dive's samples ({@code
 * log_data.calculated_values_from_samples}). Used as an independent cross-check of the PNF binary
 * decode in {@code ShearwaterPnfParserTest} rather than as import data - this project recomputes
 * its own summary from the measurements it persists.
 *
 * <p>Note the same field names also exist as columns on {@code dive_details}, where they are 0.0
 * for every row of the reference database - the values here are the populated ones.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShearwaterDbCalculatedValues(
        @JsonProperty("AverageDepth") @Nullable Double averageDepth,
        @JsonProperty("AverageTemp") @Nullable Double averageTemp,
        @JsonProperty("MinTemp") @Nullable Double minTemp,
        @JsonProperty("MaxTemp") @Nullable Double maxTemp,
        @JsonProperty("EndGF99") @Nullable Double endGf99,
        @JsonProperty("MinNDL") @Nullable Double minNdl,
        @JsonProperty("MaxDecoObligation") @Nullable Double maxDecoObligation) {}

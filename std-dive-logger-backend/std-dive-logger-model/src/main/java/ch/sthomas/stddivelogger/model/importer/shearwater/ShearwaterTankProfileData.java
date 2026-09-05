package ch.sthomas.stddivelogger.model.importer.shearwater;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shearwater Cloud's {@code dive_details.TankProfileData} JSON - the per-tank start/end pressure
 * and gas mix the diver entered in the app, plus the gas-usage windows the app derived from the
 * profile. Richer than the flat {@code Tank<n>Pressure*} columns on the same row (which carry the
 * identical pressures with no gas attached), so this is what {@code ShearwaterDbReaderService}
 * builds cylinders from.
 *
 * <p>Pressures are strings, in <b>psi</b>, and empty for a tank the diver left blank - {@link
 * #startPressurePsi()}/{@link #endPressurePsi()} on {@link TankData} are only meaningful when
 * non-blank.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShearwaterTankProfileData(
        @JsonProperty("GasProfiles") @Nullable List<GasProfile> gasProfiles,
        @JsonProperty("TankData") @Nullable List<TankData> tankData,
        @JsonProperty("CcrTankData") @Nullable List<TankData> ccrTankData) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GasProfile(
            @JsonProperty("profileIndex") int profileIndex,
            @JsonProperty("O2Percent") int o2Percent,
            @JsonProperty("HePercent") int hePercent,
            @JsonProperty("CircuitMode") int circuitMode,
            @JsonProperty("StartTimeInSeconds") @Nullable Double startTimeInSeconds,
            @JsonProperty("EndTimeInSeconds") @Nullable Double endTimeInSeconds) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TankData(
            @JsonProperty("StartPressurePSI") @Nullable String startPressurePsi,
            @JsonProperty("EndPressurePSI") @Nullable String endPressurePsi,
            @JsonProperty("GasProfile") @Nullable GasProfile gasProfile) {

        /** A tank the diver actually filled in - the app writes a full set of blank ones. */
        public boolean hasPressure() {
            return startPressurePsi != null && !startPressurePsi.isBlank();
        }
    }
}

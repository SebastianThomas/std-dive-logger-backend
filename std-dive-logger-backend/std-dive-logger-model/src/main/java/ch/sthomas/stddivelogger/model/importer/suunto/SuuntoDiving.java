package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDiving(
        @Nullable List<SuuntoGas> gases,
        // How the device calculated deco / CNS / OTU - see SuuntoJsonReaderService#decoSettings.
        @Nullable String algorithm,
        @Nullable Double minGF,
        @Nullable Double maxGF,
        @Nullable String conservatism,
        @Nullable String altitude,
        @Nullable Double surfacePressure,
        @Nullable String ascentMode,
        @Nullable Boolean deepStopEnabled,
        @Nullable String diveMode,
        @Nullable Double lastDecoStopDepth,
        @Nullable Double safetyStopTime,
        @Nullable Double noFlyTime,
        @Nullable Double desaturationTime,
        @Nullable SuuntoTissue startTissue,
        @Nullable SuuntoTissue endTissue) {

    public SuuntoDiving(final @Nullable List<SuuntoGas> gases) {
        this(
                gases, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}

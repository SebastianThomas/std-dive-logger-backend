package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

/** Device.Info: software, hardware and bootloader versions. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDeviceInfo(
        @JsonProperty("SW") @Nullable String sw,
        @JsonProperty("HW") @Nullable String hw,
        @JsonProperty("BSL") @Nullable String bsl) {}

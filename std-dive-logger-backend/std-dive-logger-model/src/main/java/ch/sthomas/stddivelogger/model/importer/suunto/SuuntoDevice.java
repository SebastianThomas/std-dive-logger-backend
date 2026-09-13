package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDevice(
        String name, @Nullable String serialNumber, @Nullable SuuntoDeviceInfo info) {

    public SuuntoDevice(final String name, final @Nullable String serialNumber) {
        this(name, serialNumber, null);
    }
}

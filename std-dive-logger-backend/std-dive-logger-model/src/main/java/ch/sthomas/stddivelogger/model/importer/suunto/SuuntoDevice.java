package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDevice(String name, @Nullable String serialNumber) {}

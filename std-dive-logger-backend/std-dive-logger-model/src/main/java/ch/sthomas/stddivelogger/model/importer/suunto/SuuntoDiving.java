package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDiving(@Nullable List<SuuntoGas> gases) {}

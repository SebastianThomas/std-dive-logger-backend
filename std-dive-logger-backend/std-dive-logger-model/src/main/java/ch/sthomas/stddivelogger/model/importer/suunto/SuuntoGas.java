package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Fractions (0-1), unlike the FIT format's dive_gas messages which use whole percent (0-100). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoGas(double oxygen, double helium) {}

package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Root of a Suunto JSON export - one top-level key. Read with {@code UPPER_CAMEL_CASE} naming
 * (configured once on the mapper, not per field), so every record below keeps plain Java names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDiveExport(SuuntoDeviceLog deviceLog) {}

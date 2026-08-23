package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 1-based index into {@code Diving.Gases} (confirmed empirically - see
 * SuuntoJsonReaderServiceTest).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoGasSwitchEvent(int gasNumber) {}

package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device's own avg/max-depth summary (meters) - only used for {@code ParsedImport}'s "guess"
 * preview field. The saved profile's depth always comes from the real per-sample measurements.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDepthSummary(double avg, double max) {}

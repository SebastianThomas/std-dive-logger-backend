package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Used for both the {@code setpoint} and {@code ppo2} graphData arrays. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftPressureSample(long timestamp, double pressureInBar) {}

package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDepthSample(long timestamp, double value) {}

package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoDeviceLog(SuuntoHeader header, List<SuuntoSample> samples) {}

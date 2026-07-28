package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftGraphData(
        @Nullable List<DivesoftDepthSample> depth,
        @Nullable List<DivesoftTemperatureSample> temperature,
        @Nullable List<DivesoftCeilingSample> ceiling,
        @Nullable List<DivesoftPressureSample> setpoint,
        @Nullable List<DivesoftPressureSample> ppo2,
        @Nullable List<DivesoftModeSample> modes,
        @Nullable List<DivesoftGraphMix> mixes) {}

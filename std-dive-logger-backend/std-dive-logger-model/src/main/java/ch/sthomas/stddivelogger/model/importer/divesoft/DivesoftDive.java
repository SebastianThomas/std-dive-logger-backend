package ch.sthomas.stddivelogger.model.importer.divesoft;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DivesoftDive(
        String id,
        @Nullable String deviceSerial,
        @Nullable String description,
        @Nullable String site,
        @Nullable Double latitude,
        @Nullable Double longitude,
        @Nullable Double maxDepth,
        @Nullable Double averageDepth,
        @Nullable String duration,
        @Nullable String startDate,
        @Nullable List<DivesoftMix> mixes,
        @Nullable Double visibility,
        @Nullable Double cns,
        @Nullable DivesoftDiveData diveData,
        @Nullable DivesoftGraphData graphData) {}

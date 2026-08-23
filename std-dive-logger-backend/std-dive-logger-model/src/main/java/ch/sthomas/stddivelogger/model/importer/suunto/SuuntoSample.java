package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One {@code Samples} entry - fields aren't all always present; some entries are events-only
 * markers with no {@code Depth}. {@code Depth} is meters, {@code Temperature} is Kelvin.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoSample(
        String timeISO8601,
        @Nullable Double depth,
        @Nullable Double temperature,
        @Nullable Double ceiling,
        @Nullable Long noDecTime,
        @Nullable Long timeToSurface,
        @Nullable List<SuuntoEvent> events) {}

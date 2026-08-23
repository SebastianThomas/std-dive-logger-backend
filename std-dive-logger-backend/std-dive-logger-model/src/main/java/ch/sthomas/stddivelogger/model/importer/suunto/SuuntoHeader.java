package ch.sthomas.stddivelogger.model.importer.suunto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.jspecify.annotations.Nullable;

/**
 * {@code duration} is whole seconds; {@code dateTime} is ISO-8601 (matches the first {@link
 * SuuntoSample#timeISO8601()}, kept separate to avoid relying on sample-list ordering).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SuuntoHeader(
        String dateTime,
        @Nullable SuuntoDepthSummary depth,
        SuuntoDevice device,
        @Nullable SuuntoDiving diving,
        double duration) {}

package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * The user's personal bests, each linked to the dive that holds it. Both null on an empty logbook.
 * Typed per record kind rather than a {@code (kind, value)} list so each field is self-describing
 * (metres vs a duration).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeRecords(@Nullable HomeRecordDive deepest, @Nullable HomeRecordDive longest) {
    public static final HomeRecords NONE = new HomeRecords(null, null);
}

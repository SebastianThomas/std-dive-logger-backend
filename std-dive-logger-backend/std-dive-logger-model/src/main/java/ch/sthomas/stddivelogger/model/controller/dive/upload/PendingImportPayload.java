package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Everything needed to actually persist a staged import once it's committed: the parsed profile
 * measurements (each already bound to a concrete {@code DiveComputer} id - computers are resolved
 * at stage time since get-or-create by serial number is idempotent and harmless even if the staged
 * import is later discarded), source-derived {@code gasConsumption}/{@code configuration} (not
 * user-overridable - UDDF computes these from the file itself), plus the remaining metadata not
 * already covered by the cheap "guess" columns on {@code PendingImportEntity} (notes, visibility,
 * named buddies).
 *
 * <p>{@code diveNumberGuess} preserves UDDF's "+"-prefixed fractional dive number auto-merge
 * convention (see {@code UddfReaderService}): when present and fractional, and the commit request
 * doesn't override the dive number, commit attaches the profile to the existing whole-numbered dive
 * instead of creating a new one.
 *
 * <p>{@code waterTypeHint}: the source's own water type for the site - filled in at commit only
 * when the site has none yet, never overwriting it (see {@code ImportService.createDive}).
 */
public record PendingImportPayload(
        List<DiveProfileUpload> profiles,
        String notes,
        Visibility visibility,
        DiveGasConsumption gasConsumption,
        DiveConfiguration configuration,
        List<String> namedBuddies,
        @Nullable DiveNumber diveNumberGuess,
        @Nullable WaterType waterTypeHint) {

    /** A source that knows nothing about the water. */
    public PendingImportPayload(
            final List<DiveProfileUpload> profiles,
            final String notes,
            final Visibility visibility,
            final DiveGasConsumption gasConsumption,
            final DiveConfiguration configuration,
            final List<String> namedBuddies,
            final @Nullable DiveNumber diveNumberGuess) {
        this(
                profiles,
                notes,
                visibility,
                gasConsumption,
                configuration,
                namedBuddies,
                diveNumberGuess,
                null);
    }

    public PendingImportPayload withProfiles(final List<DiveProfileUpload> newProfiles) {
        return new PendingImportPayload(
                newProfiles,
                notes,
                visibility,
                gasConsumption,
                configuration,
                namedBuddies,
                diveNumberGuess,
                waterTypeHint);
    }
}

package ch.sthomas.stddivelogger.model.controller.dive.upload;

import ch.sthomas.stddivelogger.model.dive.DiveNumber;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
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
 */
public record PendingImportPayload(
        List<DiveProfileUpload> profiles,
        String notes,
        Visibility visibility,
        DiveGasConsumption gasConsumption,
        DiveConfiguration configuration,
        List<String> namedBuddies,
        @Nullable DiveNumber diveNumberGuess) {}

package ch.sthomas.stddivelogger.model.importfile;

import ch.sthomas.stddivelogger.model.controller.dive.upload.DiveProfileUpload;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * A profile re-derived from its stored files, as a conflict proposes it.
 *
 * @param clockShift how far the importer now moves the profile's clock (e.g. a timezone fix)
 * @param rawActiveStartByLink each file link's new {@code raw_active_start}, stamped on apply/keep
 * @param startBefore with {@code endBefore} / {@code sampleCountBefore}: the profile the proposal
 *     was computed against - applying is refused once it changed
 */
public record ReprocessedProfile(
        DiveProfileUpload profile,
        Duration clockShift,
        Map<Long, Instant> rawActiveStartByLink,
        Instant startBefore,
        Instant endBefore,
        int sampleCountBefore) {}

package ch.sthomas.stddivelogger.service.importer;

import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportConflicts;
import ch.sthomas.stddivelogger.model.controller.dive.upload.ReimportResolution;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * "Prefer non-empty over empty, no-op when equal, ask on a real conflict" for the four dive-level
 * fields a reimport could legitimately bring in fresh data for (notes/visibility/named buddies/gas
 * consumption) - never touches site, configuration, tags, or the leader/team fields, all
 * deliberately out of scope for reimport (see DiveEntity.applyReimportResolution's doc comment).
 */
final class ReimportFieldMerge {
    private ReimportFieldMerge() {}

    static ReimportConflicts computeConflicts(
            final @Nullable String existingNotes,
            final @Nullable Visibility existingVisibility,
            final List<String> existingNamedBuddies,
            final @Nullable DiveGasConsumption existingGasConsumption,
            final String newNotes,
            final Visibility newVisibility,
            final List<String> newNamedBuddies,
            final DiveGasConsumption newGasConsumption) {
        return new ReimportConflicts(
                conflict(existingNotes, newNotes, isBlank(existingNotes), isBlank(newNotes)),
                conflict(
                        existingVisibility,
                        newVisibility,
                        existingVisibility == null || existingVisibility.equals(Visibility.EMPTY),
                        newVisibility.equals(Visibility.EMPTY)),
                buddiesConflict(existingNamedBuddies, newNamedBuddies),
                conflict(
                        existingGasConsumption,
                        newGasConsumption,
                        existingGasConsumption == null
                                || existingGasConsumption.equals(DiveGasConsumption.EMPTY),
                        newGasConsumption.equals(DiveGasConsumption.EMPTY)));
    }

    static @Nullable String resolveNotes(
            final @Nullable String existing,
            final String reimported,
            final ReimportResolution.@Nullable Choice choice) {
        return resolve(
                existing, reimported, isBlank(existing), isBlank(reimported), choice, "notes");
    }

    static @Nullable Visibility resolveVisibility(
            final @Nullable Visibility existing,
            final Visibility reimported,
            final ReimportResolution.@Nullable Choice choice) {
        return resolve(
                existing,
                reimported,
                existing == null || existing.equals(Visibility.EMPTY),
                reimported.equals(Visibility.EMPTY),
                choice,
                "visibility");
    }

    static @Nullable DiveGasConsumption resolveGasConsumption(
            final @Nullable DiveGasConsumption existing,
            final DiveGasConsumption reimported,
            final ReimportResolution.@Nullable Choice choice) {
        return resolve(
                existing,
                reimported,
                existing == null || existing.equals(DiveGasConsumption.EMPTY),
                reimported.equals(DiveGasConsumption.EMPTY),
                choice,
                "gas consumption");
    }

    static @Nullable List<String> resolveNamedBuddies(
            final List<String> existing,
            final List<String> reimported,
            final ReimportResolution.@Nullable BuddiesChoice choice) {
        if (reimported.isEmpty()) {
            return null;
        }
        if (existing.isEmpty() || sameNames(existing, reimported)) {
            return existing.isEmpty() ? reimported : null;
        }
        if (choice == null) {
            throw new IllegalArgumentException(
                    "Reimport has conflicting named buddies - a resolution is required");
        }
        return switch (choice) {
            case EXISTING -> null;
            case NEW -> reimported;
            case UNION -> Stream.concat(existing.stream(), reimported.stream()).distinct().toList();
        };
    }

    private static <T> ReimportConflicts.@Nullable FieldConflict<T> conflict(
            final @Nullable T existing,
            final T reimported,
            final boolean existingEmpty,
            final boolean reimportedEmpty) {
        if (existingEmpty || reimportedEmpty || existing == null || existing.equals(reimported)) {
            return null;
        }
        return new ReimportConflicts.FieldConflict<>(existing, reimported);
    }

    private static ReimportConflicts.@Nullable FieldConflict<List<String>> buddiesConflict(
            final List<String> existing, final List<String> reimported) {
        if (existing.isEmpty() || reimported.isEmpty() || sameNames(existing, reimported)) {
            return null;
        }
        return new ReimportConflicts.FieldConflict<>(existing, reimported);
    }

    private static boolean sameNames(final List<String> a, final List<String> b) {
        return new HashSet<>(a).equals(new HashSet<>(b));
    }

    private static <T> @Nullable T resolve(
            final @Nullable T existing,
            final T reimported,
            final boolean existingEmpty,
            final boolean reimportedEmpty,
            final ReimportResolution.@Nullable Choice choice,
            final String fieldName) {
        if (reimportedEmpty) {
            return null;
        }
        if (existingEmpty) {
            return reimported;
        }
        if (Objects.equals(existing, reimported)) {
            return null;
        }
        if (choice == null) {
            throw new IllegalArgumentException(
                    "Reimport has a conflicting " + fieldName + " - a resolution is required");
        }
        return switch (choice) {
            case EXISTING -> null;
            case NEW -> reimported;
        };
    }

    private static boolean isBlank(final @Nullable String s) {
        return s == null || s.isBlank();
    }
}

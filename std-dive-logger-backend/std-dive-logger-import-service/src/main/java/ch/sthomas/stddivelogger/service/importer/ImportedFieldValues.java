package ch.sthomas.stddivelogger.service.importer;

import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.BUDDIES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.GAS_CONSUMPTION;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.NOTES;
import static ch.sthomas.stddivelogger.model.importfile.ImportedDiveField.VISIBILITY;

import ch.sthomas.stddivelogger.model.controller.dive.upload.PendingImportPayload;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.NamedBuddy;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importfile.ImportedDiveField;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.jspecify.annotations.Nullable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Dive-level values as a file provides them and as the dive holds them - the JSON kept in {@code
 * t_dive_import_file_field} and how re-processing compares it. Stored values went through the
 * database, so comparisons are semantic (blank = empty, small float noise ignored).
 */
public final class ImportedFieldValues {
    static final JsonMapper JSON = ObjectMapperUtils.objectMapperBuilder(_ -> {}).build();

    /** How a dive site guess from a file is recorded. */
    public record SiteValue(String name, @Nullable Double latitude, @Nullable Double longitude) {}

    private ImportedFieldValues() {}

    /** Records {@code value} for {@code field}, unless it's empty. */
    public static void putTaken(
            final Map<ImportedDiveField, JsonNode> taken,
            final ImportedDiveField field,
            final @Nullable Object value) {
        if (value == null) {
            return;
        }
        final Object normalized =
                field == BUDDIES && value instanceof final List<?> names
                        ? sortedNames(names)
                        : value;
        final JsonNode node = JSON.valueToTree(normalized);
        if (!isEmpty(field, node)) {
            taken.put(field, node);
        }
    }

    /** What the file says for the fields re-processing may update; empty ones left out. */
    public static Map<ImportedDiveField, JsonNode> reprocessable(
            final PendingImportPayload payload) {
        final var values = new EnumMap<ImportedDiveField, JsonNode>(ImportedDiveField.class);
        putTaken(values, NOTES, payload.notes());
        putTaken(values, VISIBILITY, payload.visibility());
        putTaken(values, BUDDIES, payload.namedBuddies());
        putTaken(values, GAS_CONSUMPTION, payload.gasConsumption());
        return values;
    }

    /** The dive's value of each re-processable field, empty ones included. */
    public static Map<ImportedDiveField, JsonNode> current(final Dive dive) {
        final var values = new EnumMap<ImportedDiveField, JsonNode>(ImportedDiveField.class);
        values.put(NOTES, JSON.valueToTree(Objects.requireNonNullElse(dive.notes(), "")));
        values.put(
                VISIBILITY,
                JSON.valueToTree(Optional.ofNullable(dive.visibility()).orElse(Visibility.EMPTY)));
        values.put(
                BUDDIES,
                JSON.valueToTree(
                        sortedNames(dive.namedBuddies().stream().map(NamedBuddy::name).toList())));
        values.put(
                GAS_CONSUMPTION,
                JSON.valueToTree(
                        Optional.ofNullable(dive.gasConsumption())
                                .orElse(DiveGasConsumption.EMPTY)));
        return values;
    }

    public static boolean isEmpty(final ImportedDiveField field, final @Nullable JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return true;
        }
        return switch (field) {
            case NOTES, DIVE_IDENTIFIER -> notes(node).isBlank();
            case VISIBILITY -> {
                final var v = visibility(node);
                yield v.meters() == null && isBlank(v.description()) && v.feeling() == null;
            }
            case BUDDIES -> buddies(node).isEmpty();
            case GAS_CONSUMPTION -> {
                final var g = gasConsumption(node);
                yield g.sacBar() == 0 && g.rmvLiters() == 0 && g.totalLiters() == 0;
            }
            case CONFIGURATION, SITE, DIVE_NUMBER -> false;
        };
    }

    public static boolean same(
            final ImportedDiveField field, final @Nullable JsonNode a, final @Nullable JsonNode b) {
        if (a == null || b == null || isEmpty(field, a) || isEmpty(field, b)) {
            return isEmpty(field, a) && isEmpty(field, b);
        }
        return switch (field) {
            case NOTES, DIVE_IDENTIFIER -> notes(a).strip().equals(notes(b).strip());
            case VISIBILITY -> sameVisibility(visibility(a), visibility(b));
            case BUDDIES -> new HashSet<>(buddies(a)).equals(new HashSet<>(buddies(b)));
            case GAS_CONSUMPTION -> sameGasConsumption(gasConsumption(a), gasConsumption(b));
            case CONFIGURATION, SITE, DIVE_NUMBER -> a.equals(b);
        };
    }

    /** "Notes: “old” → “new”" - what a conflict shows. */
    public static String describeChange(
            final ImportedDiveField field, final @Nullable JsonNode from, final JsonNode to) {
        return label(field) + ": " + describe(field, from) + " → " + describe(field, to);
    }

    public static String notes(final JsonNode node) {
        return Objects.requireNonNullElse(JSON.treeToValue(node, String.class), "");
    }

    public static Visibility visibility(final JsonNode node) {
        return Objects.requireNonNullElse(
                JSON.treeToValue(node, Visibility.class), Visibility.EMPTY);
    }

    public static List<String> buddies(final JsonNode node) {
        final var names = JSON.treeToValue(node, String[].class);
        return names == null ? List.of() : sortedNames(Arrays.asList(names));
    }

    public static DiveGasConsumption gasConsumption(final JsonNode node) {
        return Objects.requireNonNullElse(
                JSON.treeToValue(node, DiveGasConsumption.class), DiveGasConsumption.EMPTY);
    }

    private static String label(final ImportedDiveField field) {
        return switch (field) {
            case NOTES -> "Notes";
            case VISIBILITY -> "Visibility";
            case BUDDIES -> "Buddies";
            case GAS_CONSUMPTION -> "Gas consumption";
            case CONFIGURATION -> "Gear configuration";
            case SITE -> "Dive site";
            case DIVE_NUMBER -> "Dive number";
            case DIVE_IDENTIFIER -> "Dive name";
        };
    }

    private static String describe(final ImportedDiveField field, final @Nullable JsonNode node) {
        if (isEmpty(field, node)) {
            return "(empty)";
        }
        final var value = Objects.requireNonNull(node);
        return switch (field) {
            case NOTES, DIVE_IDENTIFIER -> "“" + abbreviate(notes(value).strip()) + "”";
            case VISIBILITY -> {
                final var v = visibility(value);
                final var parts = new java.util.ArrayList<String>();
                if (v.meters() != null) {
                    parts.add(String.format("%.0f m", v.meters()));
                }
                if (!isBlank(v.description())) {
                    parts.add(abbreviate(Objects.requireNonNull(v.description()).strip()));
                }
                if (v.feeling() != null) {
                    parts.add(v.feeling().name().toLowerCase(java.util.Locale.ROOT));
                }
                yield String.join(", ", parts);
            }
            case BUDDIES -> String.join(", ", buddies(value));
            case GAS_CONSUMPTION -> {
                final var g = gasConsumption(value);
                yield String.format("SAC %.1f bar/min, RMV %.1f l/min", g.sacBar(), g.rmvLiters());
            }
            case CONFIGURATION, SITE, DIVE_NUMBER -> value.toString();
        };
    }

    private static String abbreviate(final String text) {
        return text.length() <= 80 ? text : text.substring(0, 79) + "…";
    }

    private static List<String> sortedNames(final List<?> names) {
        return names.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::strip)
                .filter(n -> !n.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean sameVisibility(final Visibility a, final Visibility b) {
        final var metersA = a.meters();
        final var metersB = b.meters();
        final var sameMeters =
                metersA == null || metersB == null
                        ? metersA == metersB
                        : Math.abs(metersA - metersB) <= 0.01;
        return sameMeters
                && Objects.requireNonNullElse(a.description(), "")
                        .strip()
                        .equals(Objects.requireNonNullElse(b.description(), "").strip())
                && a.feeling() == b.feeling();
    }

    private static boolean sameGasConsumption(
            final DiveGasConsumption a, final DiveGasConsumption b) {
        return Math.abs(a.sacBar() - b.sacBar()) <= 0.01
                && Math.abs(a.rmvLiters() - b.rmvLiters()) <= 0.01
                && Math.abs(a.totalLiters() - b.totalLiters()) <= 0.5;
    }

    private static boolean isBlank(final @Nullable String s) {
        return s == null || s.isBlank();
    }
}

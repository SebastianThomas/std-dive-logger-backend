package ch.sthomas.stddivelogger.model.dive.conditions;

import jakarta.annotation.Nullable;

import java.util.Map;

public enum VisibilityFeeling {
    HIGH,
    AVERAGE,
    LOW;

    private static final Map<String, VisibilityFeeling> map =
            Map.ofEntries(
                    Map.entry("high", HIGH),
                    Map.entry("good", HIGH),
                    Map.entry("avg", AVERAGE),
                    Map.entry("average", AVERAGE),
                    Map.entry("poor", LOW),
                    Map.entry("low", LOW));

    @Nullable
    public static VisibilityFeeling from(final String string) {
        final var trimmed = string.trim().toLowerCase();
        if (trimmed.startsWith("very")) {
            return map.get(trimmed.substring(4).trim());
        }
        return map.get(trimmed);
    }
}

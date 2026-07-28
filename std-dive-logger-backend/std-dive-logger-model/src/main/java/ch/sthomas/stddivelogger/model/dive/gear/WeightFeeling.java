package ch.sthomas.stddivelogger.model.dive.gear;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public enum WeightFeeling {
    LIGHT,
    GOOD,
    HEAVY;

    private static final Map<String, WeightFeeling> map =
            Map.ofEntries(
                    Map.entry("light", LIGHT), Map.entry("good", GOOD), Map.entry("heavy", HEAVY));

    @Nullable
    public WeightFeeling from(final String s) {
        return map.get(s.trim().toLowerCase());
    }
}

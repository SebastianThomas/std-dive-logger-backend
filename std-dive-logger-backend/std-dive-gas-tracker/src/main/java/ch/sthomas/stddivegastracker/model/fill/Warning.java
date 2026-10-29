package ch.sthomas.stddivegastracker.model.fill;

import org.jspecify.annotations.Nullable;

/** API.md shared type {@code Warning}: the fill is saved anyway (DOMAIN §5.2, §5.6). */
public record Warning(Code code, String message, @Nullable Integer stageIndex) {

    public enum Code {
        ANALYSIS_MISMATCH,
        SOURCE_OVERDRAWN
    }
}

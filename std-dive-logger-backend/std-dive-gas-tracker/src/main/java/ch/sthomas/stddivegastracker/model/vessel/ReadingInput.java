package ch.sthomas.stddivegastracker.model.vessel;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** API.md §3 {@code POST …/readings} body. */
public record ReadingInput(Double pressureBar, @Nullable Instant measuredAt) {

    public ReadingInput {
        pressureBar = Limits.pressureBar(pressureBar, "Pressure");
    }
}

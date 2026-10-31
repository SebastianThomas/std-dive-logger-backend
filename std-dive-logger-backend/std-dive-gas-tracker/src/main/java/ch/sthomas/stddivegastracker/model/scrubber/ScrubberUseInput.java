package ch.sthomas.stddivegastracker.model.scrubber;

import ch.sthomas.stddivegastracker.model.vessel.Limits;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** API.md §7. An absent {@code memberId} is 0, which no member has (400 "Unknown member"). */
public record ScrubberUseInput(long memberId, double kg, @Nullable Instant usedAt) {

    public ScrubberUseInput {
        Limits.kg(kg, "Amount");
    }
}

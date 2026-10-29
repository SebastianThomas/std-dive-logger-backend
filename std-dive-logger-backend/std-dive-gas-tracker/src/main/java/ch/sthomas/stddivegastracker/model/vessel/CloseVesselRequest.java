package ch.sthomas.stddivegastracker.model.vessel;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** API.md §3 {@code POST …/close} body (DOMAIN §6: the closing pressure is required). */
public record CloseVesselRequest(Double closingPressureBar, @Nullable Instant closedAt) {

    public CloseVesselRequest {
        closingPressureBar = Limits.pressureBar(closingPressureBar, "Closing pressure");
    }
}

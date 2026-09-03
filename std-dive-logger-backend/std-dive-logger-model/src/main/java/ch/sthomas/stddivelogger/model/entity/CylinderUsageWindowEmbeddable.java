package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * One row of {@code t_dive_configuration_cylinder_usage_window} - see {@link CylinderUsageWindow}.
 */
@Embeddable
@SuppressWarnings("NullAway.Init")
public class CylinderUsageWindowEmbeddable {

    @Column(name = "window_start")
    private @Nullable Instant windowStart;

    @Column(name = "window_end")
    private @Nullable Instant windowEnd;

    public CylinderUsageWindowEmbeddable() {}

    public CylinderUsageWindowEmbeddable(final CylinderUsageWindow window) {
        this.windowStart = window.start();
        this.windowEnd = window.end();
    }

    public CylinderUsageWindow toRecord() {
        return new CylinderUsageWindow(windowStart, windowEnd);
    }

    /**
     * Moves both bounds by {@code delta} - so a cylinder's timed stretches follow a re-dated dive.
     */
    public void shiftBy(final Duration delta) {
        if (windowStart != null) {
            this.windowStart = windowStart.plus(delta);
        }
        if (windowEnd != null) {
            this.windowEnd = windowEnd.plus(delta);
        }
    }
}

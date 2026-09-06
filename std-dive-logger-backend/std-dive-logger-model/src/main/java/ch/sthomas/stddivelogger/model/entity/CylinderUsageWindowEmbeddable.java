package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * One row of {@code t_dive_configuration_cylinder_usage_window} - see {@link CylinderUsageWindow}.
 */
@Embeddable
@SuppressWarnings("NullAway.Init")
public class CylinderUsageWindowEmbeddable {

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "start_offset")
    private @Nullable Duration windowStart;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "end_offset")
    private @Nullable Duration windowEnd;

    public CylinderUsageWindowEmbeddable() {}

    public CylinderUsageWindowEmbeddable(final CylinderUsageWindow window) {
        this.windowStart = window.start();
        this.windowEnd = window.end();
    }

    public CylinderUsageWindow toRecord() {
        return new CylinderUsageWindow(windowStart, windowEnd);
    }

    public void rebaseUsageWindows(final java.time.Duration delta) {
        if (windowStart != null) windowStart = windowStart.plus(delta);
        if (windowEnd != null) windowEnd = windowEnd.plus(delta);
    }
}

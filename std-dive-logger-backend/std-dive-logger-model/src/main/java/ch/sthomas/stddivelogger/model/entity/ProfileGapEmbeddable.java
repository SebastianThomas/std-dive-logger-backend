package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.profile.TimeRange;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

/** One row of {@code t_dive_profile_gap}: offsets from the profile start, like its samples. */
@Embeddable
@SuppressWarnings("NullAway.Init")
public class ProfileGapEmbeddable {

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "start_offset", nullable = false)
    private Duration startOffset;

    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    @Column(name = "end_offset", nullable = false)
    private Duration endOffset;

    public ProfileGapEmbeddable() {}

    public ProfileGapEmbeddable(final TimeRange gap, final Instant profileStart) {
        this.startOffset = Duration.between(profileStart, gap.start());
        this.endOffset = Duration.between(profileStart, gap.end());
    }

    public TimeRange toRecord(final Instant profileStart) {
        return new TimeRange(profileStart.plus(startOffset), profileStart.plus(endOffset));
    }
}

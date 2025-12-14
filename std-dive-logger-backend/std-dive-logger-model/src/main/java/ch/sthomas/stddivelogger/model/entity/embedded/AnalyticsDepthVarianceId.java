package ch.sthomas.stddivelogger.model.entity.embedded;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class AnalyticsDepthVarianceId implements Serializable {

    @Column(name = "fk_profile_segment_id")
    private Long profileSegmentId;

    @Column(name = "version", nullable = false)
    private Long version;

    public AnalyticsDepthVarianceId() {}

    public AnalyticsDepthVarianceId(final AnalyticsDepthVariance record) {
        this.profileSegmentId = record.segmentWithId().id();
        this.version = record.stats().version();
    }

    public Long getVersion() {
        return version;
    }
}

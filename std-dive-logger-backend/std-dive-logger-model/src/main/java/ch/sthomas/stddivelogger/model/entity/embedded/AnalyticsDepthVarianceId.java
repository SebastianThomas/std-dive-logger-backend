package ch.sthomas.stddivelogger.model.entity.embedded;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class AnalyticsDepthVarianceId implements Serializable {

    @Column(name = "fk_profile_id")
    private Long profileId;

    @Column(name = "fk_measurement_start")
    private Long measurementStartId;

    @Column(name = "fk_measurement_end")
    private Long measurementEndId;

    public AnalyticsDepthVarianceId() {}

    public AnalyticsDepthVarianceId(final AnalyticsDepthVariance record) {
        this.profileId = record.profile().id();
        this.measurementStartId = record.measurementStart().id();
        this.measurementEndId = record.measurementEnd().id();
    }
}

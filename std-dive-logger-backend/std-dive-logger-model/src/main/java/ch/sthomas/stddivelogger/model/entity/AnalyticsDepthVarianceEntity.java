package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.entity.embedded.AnalyticsDepthVarianceId;

import jakarta.persistence.*;

@Entity
@Table(name = "t_analytics_depth_variance")
public class AnalyticsDepthVarianceEntity {
    @EmbeddedId private AnalyticsDepthVarianceId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("profileId")
    @JoinColumn(name = "fk_profile_id", nullable = false)
    private DiveProfileEntity profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("measurementStartId")
    @JoinColumn(name = "fk_measurement_start", nullable = false)
    private DiveMeasurementEntity measurementStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("measurementEndId")
    @JoinColumn(name = "fk_measurement_end", nullable = false)
    private DiveMeasurementEntity measurementEnd;

    @Column(name = "avg_depth", nullable = false)
    private Double avgDepth;

    @Column(name = "max_depth", nullable = false)
    private Double maxDepth;

    @Column(name = "min_depth", nullable = false)
    private Double minDepth;

    @Column(name = "deviation_avg", nullable = false)
    private Double deviationAvg;

    @Column(name = "deviation_variance", nullable = false)
    private Double deviationVariance;

    @Column(name = "deviation_01p", nullable = false)
    private Double deviation01p;

    @Column(name = "deviation_10p", nullable = false)
    private Double deviation10p;

    @Column(name = "deviation_median", nullable = false)
    private Double deviationMedian;

    @Column(name = "deviation_90p", nullable = false)
    private Double deviation90p;

    // Generated always by DB
    @Column(name = "deviation_max", nullable = false, insertable = false, updatable = false)
    private Double deviationMax;

    public AnalyticsDepthVarianceEntity() {}

    public AnalyticsDepthVarianceEntity(final AnalyticsDepthVariance record) {
        this.id = new AnalyticsDepthVarianceId(record);
        this.measurementStart = new DiveMeasurementEntity(record.measurementStart());
        this.measurementEnd = new DiveMeasurementEntity(record.measurementEnd());
        this.avgDepth = record.avgDepth();
        this.maxDepth = record.maxDepth();
        this.minDepth = record.minDepth();
        this.deviationAvg = record.deviationAvg();
        this.deviationVariance = record.deviationVariance();
        this.deviation01p = record.deviation01p();
        this.deviation10p = record.deviation10p();
        this.deviationMedian = record.deviationMedian();
        this.deviation90p = record.deviation90p();
        this.deviationMax = record.deviationMax();
    }

    public AnalyticsDepthVariance toRecord() {
        return new AnalyticsDepthVariance(
                profile.toRecord(),
                measurementStart.toRecordWithId(),
                measurementEnd.toRecordWithId(),
                id.getVersion(),
                avgDepth,
                maxDepth,
                minDepth,
                deviationAvg,
                deviationVariance,
                deviation01p,
                deviation10p,
                deviationMedian,
                deviation90p,
                deviationMax);
    }
}

package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVariance;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceResponse;
import ch.sthomas.stddivelogger.model.analytics.AnalyticsDepthVarianceStats;
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

    @Column(name = "start_idx", nullable = false)
    private int startIdx;

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

    public AnalyticsDepthVarianceEntity(
            final AnalyticsDepthVariance record, final DiveProfileEntity profileEntity) {
        this.id = new AnalyticsDepthVarianceId(record);
        this.profile = profileEntity;
        this.measurementStart = new DiveMeasurementEntity(record.measurementStart());
        this.measurementEnd = new DiveMeasurementEntity(record.measurementEnd());
        this.startIdx = record.startIdx();
        this.avgDepth = record.stats().avgDepth();
        this.maxDepth = record.stats().maxDepth();
        this.minDepth = record.stats().minDepth();
        this.deviationAvg = record.stats().deviationAvg();
        this.deviationVariance = record.stats().deviationVariance();
        this.deviation01p = record.stats().deviation01p();
        this.deviation10p = record.stats().deviation10p();
        this.deviationMedian = record.stats().deviationMedian();
        this.deviation90p = record.stats().deviation90p();
        this.deviationMax = record.stats().deviationMax();
    }

    public AnalyticsDepthVariance toRecord() {
        return new AnalyticsDepthVariance(
                profile.toRecord(),
                measurementStart.toRecordWithId(),
                measurementEnd.toRecordWithId(),
                startIdx,
                toStats());
    }

    public AnalyticsDepthVarianceResponse toResponse() {
        return new AnalyticsDepthVarianceResponse(
                profile.getDiveId(), profile.getId(), startIdx, toStats());
    }

    public AnalyticsDepthVarianceStats toStats() {
        return new AnalyticsDepthVarianceStats(
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

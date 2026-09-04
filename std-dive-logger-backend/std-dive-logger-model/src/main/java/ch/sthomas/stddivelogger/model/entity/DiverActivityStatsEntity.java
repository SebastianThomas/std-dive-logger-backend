package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.home.DiverActivityStats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * The cached home-dashboard activity/trend blob for one diver (see {@link DiverActivityStats}).
 * Recomputed by the analytics deployable only when {@link #sourceFingerprint} no longer matches the
 * diver's dives, or when {@link #computedVersion} is behind {@link DiverActivityStats#VERSION}.
 */
@Entity
@Table(name = "t_diver_activity_stats")
@SuppressWarnings("NullAway.Init")
public class DiverActivityStatsEntity {

    @Id
    @Column(name = "fk_diver_id")
    private Long diverId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "computed_version", nullable = false)
    private int computedVersion;

    @Column(name = "source_fingerprint", nullable = false)
    private String sourceFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stats", nullable = false)
    private DiverActivityStats stats;

    public DiverActivityStatsEntity() {}

    public DiverActivityStatsEntity(
            final long diverId,
            final Instant computedAt,
            final int computedVersion,
            final String sourceFingerprint,
            final DiverActivityStats stats) {
        this.diverId = diverId;
        this.computedAt = computedAt;
        this.computedVersion = computedVersion;
        this.sourceFingerprint = sourceFingerprint;
        this.stats = stats;
    }

    public void update(
            final Instant computedAt,
            final int computedVersion,
            final String sourceFingerprint,
            final DiverActivityStats stats) {
        this.computedAt = computedAt;
        this.computedVersion = computedVersion;
        this.sourceFingerprint = sourceFingerprint;
        this.stats = stats;
    }

    public Long getDiverId() {
        return diverId;
    }

    public DiverActivityStats getStats() {
        return stats;
    }

    public int getComputedVersion() {
        return computedVersion;
    }

    public String getSourceFingerprint() {
        return sourceFingerprint;
    }
}

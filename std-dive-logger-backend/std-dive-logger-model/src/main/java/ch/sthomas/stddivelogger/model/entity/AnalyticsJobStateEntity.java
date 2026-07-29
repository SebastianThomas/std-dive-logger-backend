package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "t_analytics_job_state",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"fk_dive_id", "module", "job_name"}))
@SuppressWarnings("NullAway.Init")
public class AnalyticsJobStateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_analytics_job_state_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_dive_id", nullable = false)
    private DiveEntity dive;

    @Column(name = "module", nullable = false)
    private String module;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    public AnalyticsJobStateEntity() {}

    public AnalyticsJobStateEntity(
            final DiveEntity dive,
            final String module,
            final String jobName,
            final long version,
            final Instant computedAt) {
        this.dive = dive;
        this.module = module;
        this.jobName = jobName;
        this.version = version;
        this.computedAt = computedAt.atOffset(UTC);
    }

    public void setVersion(final long version) {
        this.version = version;
    }

    public void setComputedAt(final Instant computedAt) {
        this.computedAt = computedAt.atOffset(UTC);
    }
}

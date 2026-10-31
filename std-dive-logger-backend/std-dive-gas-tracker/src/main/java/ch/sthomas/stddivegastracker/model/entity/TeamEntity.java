package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Tenant and accounting unit with its settings (DOMAIN §2). */
@Entity
@Table(name = "t_team")
@SuppressWarnings("NullAway.Init")
public class TeamEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_team_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_loss_policy", nullable = false, length = 10)
    private LossPolicy defaultLossPolicy;

    @Column(name = "line_dead_volume_litres", nullable = false)
    private double lineDeadVolumeLitres;

    @Column(name = "default_residual_pressure_bar", nullable = false)
    private double defaultResidualPressureBar;

    @Column(name = "gas_tracking_enabled", nullable = false)
    private boolean gasTrackingEnabled = true;

    @Column(name = "scrubber_tracking_enabled", nullable = false)
    private boolean scrubberTrackingEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TeamEntity() {}

    /** Dead volume and residual pressure start at 0 (= feature off), set them if needed. */
    public TeamEntity(
            final String name, final String currency, final LossPolicy defaultLossPolicy) {
        this.name = name;
        this.currency = currency;
        this.defaultLossPolicy = defaultLossPolicy;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public LossPolicy getDefaultLossPolicy() {
        return defaultLossPolicy;
    }

    public void setDefaultLossPolicy(final LossPolicy defaultLossPolicy) {
        this.defaultLossPolicy = defaultLossPolicy;
    }

    public double getLineDeadVolumeLitres() {
        return lineDeadVolumeLitres;
    }

    public void setLineDeadVolumeLitres(final double lineDeadVolumeLitres) {
        this.lineDeadVolumeLitres = lineDeadVolumeLitres;
    }

    public double getDefaultResidualPressureBar() {
        return defaultResidualPressureBar;
    }

    public void setDefaultResidualPressureBar(final double defaultResidualPressureBar) {
        this.defaultResidualPressureBar = defaultResidualPressureBar;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setGasTrackingEnabled(final boolean gasTrackingEnabled) {
        this.gasTrackingEnabled = gasTrackingEnabled;
    }

    public void setScrubberTrackingEnabled(final boolean scrubberTrackingEnabled) {
        this.scrubberTrackingEnabled = scrubberTrackingEnabled;
    }

    public boolean isGasTrackingEnabled() {
        return gasTrackingEnabled;
    }

    /** The team as seen by {@code caller} (API.md §1 {@code Team} carries the caller's role). */
    public Team toRecord(final TeamMembership caller) {
        return new Team(
                id,
                name,
                caller.role(),
                caller.memberId(),
                currency,
                defaultLossPolicy,
                lineDeadVolumeLitres,
                defaultResidualPressureBar,
                gasTrackingEnabled,
                scrubberTrackingEnabled);
    }
}

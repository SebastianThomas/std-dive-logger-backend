package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.calc.allocation.LossPolicy;
import ch.sthomas.stddivegastracker.calc.gas.GasMix;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A bank or house-mix container with its cost lines, readings and (once closed) the loss
 * participants snapshot. CLOSED means {@code closedAt != null} (DOMAIN §2, §6, §7.3).
 */
@Entity
@Table(name = "t_vessel")
@SuppressWarnings("NullAway.Init")
public class VesselEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_vessel_id", nullable = false)
    private Long id;

    @Column(name = "fk_team_id", nullable = false)
    private Long teamId;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "water_volume_litres", nullable = false)
    private double waterVolumeLitres;

    @Column(name = "mix_o2", nullable = false)
    private double mixO2;

    @Column(name = "mix_he", nullable = false)
    private double mixHe;

    /** The gas at {@code initialPressureBar} when registered (DOMAIN §6 step 1). */
    @Column(name = "initial_o2", nullable = false)
    private double initialO2;

    @Column(name = "initial_he", nullable = false)
    private double initialHe;

    @Column(name = "initial_pressure_bar", nullable = false)
    private double initialPressureBar;

    @Column(name = "expected_residual_pressure_bar", nullable = false)
    private double expectedResidualPressureBar;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_policy", nullable = false, length = 10)
    private LossPolicy lossPolicy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private @Nullable Instant closedAt;

    @Column(name = "closing_pressure_bar")
    private @Nullable Double closingPressureBar;

    @Column(name = "notes", length = 1000)
    private @Nullable String notes;

    @Column(name = "fk_created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_vessel_id", nullable = false, updatable = false)
    @OrderBy("paidAt ASC, id ASC")
    private List<VesselCostLineEntity> costLines = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_vessel_id", nullable = false, updatable = false)
    @OrderBy("measuredAt ASC, id ASC")
    private List<VesselReadingEntity> readings = new ArrayList<>();

    /** Member ids snapshotted when the vessel was closed (DOMAIN §7.3); empty while open. */
    @ElementCollection
    @CollectionTable(
            name = "t_vessel_loss_participant",
            joinColumns = @JoinColumn(name = "fk_vessel_id"))
    @Column(name = "fk_member_id", nullable = false)
    private Set<Long> lossParticipantMemberIds = new HashSet<>();

    public VesselEntity() {}

    public VesselEntity(
            final Long teamId,
            final Long createdByMemberId,
            final String label,
            final double waterVolumeLitres,
            final GasMix mix,
            final double initialPressureBar,
            final double expectedResidualPressureBar,
            final LossPolicy lossPolicy,
            final Instant openedAt) {
        this.teamId = teamId;
        this.createdByMemberId = createdByMemberId;
        this.label = label;
        this.waterVolumeLitres = waterVolumeLitres;
        this.mixO2 = mix.o2();
        this.mixHe = mix.he();
        this.initialO2 = mix.o2();
        this.initialHe = mix.he();
        this.initialPressureBar = initialPressureBar;
        this.expectedResidualPressureBar = expectedResidualPressureBar;
        this.lossPolicy = lossPolicy;
        this.openedAt = openedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }

    public double getWaterVolumeLitres() {
        return waterVolumeLitres;
    }

    /** Declared mix (DOMAIN §5.1). */
    public GasMix getMix() {
        return new GasMix(mixO2, mixHe);
    }

    public void setMix(final GasMix mix) {
        this.mixO2 = mix.o2();
        this.mixHe = mix.he();
    }

    public GasMix getInitialMix() {
        return new GasMix(initialO2, initialHe);
    }

    /** A correction of what was registered; only while nothing was blended into the vessel. */
    public void setInitialMix(final GasMix mix) {
        this.initialO2 = mix.o2();
        this.initialHe = mix.he();
    }

    public double getInitialPressureBar() {
        return initialPressureBar;
    }

    public double getExpectedResidualPressureBar() {
        return expectedResidualPressureBar;
    }

    public void setExpectedResidualPressureBar(final double expectedResidualPressureBar) {
        this.expectedResidualPressureBar = expectedResidualPressureBar;
    }

    public LossPolicy getLossPolicy() {
        return lossPolicy;
    }

    public void setLossPolicy(final LossPolicy lossPolicy) {
        this.lossPolicy = lossPolicy;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public @Nullable Instant getClosedAt() {
        return closedAt;
    }

    public @Nullable Double getClosingPressureBar() {
        return closingPressureBar;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public void setNotes(final @Nullable String notes) {
        this.notes = notes;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<VesselCostLineEntity> getCostLines() {
        return costLines;
    }

    public List<VesselReadingEntity> getReadings() {
        return readings;
    }

    public Set<Long> getLossParticipantMemberIds() {
        return lossParticipantMemberIds;
    }

    public boolean isClosed() {
        return closedAt != null;
    }

    /** In service at {@code at}: {@code [openedAt, closedAt)}, as for fills (DOMAIN §5.6). */
    public boolean isInServiceAt(final Instant at) {
        return !openedAt.isAfter(at) && (closedAt == null || at.isBefore(closedAt));
    }

    /** Closed time and closing pressure are always set together (see the DB check). */
    public void close(final Instant closedAt, final double closingPressureBar) {
        this.closedAt = closedAt;
        this.closingPressureBar = closingPressureBar;
    }

    /** Clears the closing data and the loss participants snapshot (DOMAIN §6). */
    public void reopen() {
        this.closedAt = null;
        this.closingPressureBar = null;
        lossParticipantMemberIds.clear();
    }
}

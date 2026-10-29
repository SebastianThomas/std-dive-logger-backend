package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.fill.FillContent;
import ch.sthomas.stddivegastracker.model.fill.FillTarget;
import ch.sthomas.stddivegastracker.model.fill.StoredFill;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One filling operation with its stages (DOMAIN §5, §5.4; no money stored). Target: a vessel, else
 * a diver's cylinder (water volume + charged member). VOIDED means {@code voidedAt != null}.
 */
@Entity
@Table(name = "t_fill")
@SuppressWarnings("NullAway.Init")
public class FillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_fill_id", nullable = false)
    private Long id;

    @Column(name = "fk_team_id", nullable = false)
    private Long teamId;

    @Column(name = "filled_at", nullable = false)
    private Instant filledAt;

    @Column(name = "fk_recorded_by_member_id", nullable = false)
    private Long recordedByMemberId;

    @Column(name = "fk_target_vessel_id")
    private @Nullable Long targetVesselId;

    @Column(name = "water_volume_litres")
    private @Nullable Double waterVolumeLitres;

    @Column(name = "cylinder_label", length = 60)
    private @Nullable String cylinderLabel;

    @Column(name = "fk_charged_member_id")
    private @Nullable Long chargedMemberId;

    @Column(name = "start_pressure_bar", nullable = false)
    private double startPressureBar;

    @Column(name = "start_o2", nullable = false)
    private double startO2;

    @Column(name = "start_he", nullable = false)
    private double startHe;

    @Column(name = "end_pressure_bar", nullable = false)
    private double endPressureBar;

    @Column(name = "end_o2", nullable = false)
    private double endO2;

    @Column(name = "end_he", nullable = false)
    private double endHe;

    @Column(name = "end_analysis_o2")
    private @Nullable Double endAnalysisO2;

    @Column(name = "end_analysis_he")
    private @Nullable Double endAnalysisHe;

    @Column(name = "notes", length = 1000)
    private @Nullable String notes;

    @Column(name = "voided_at")
    private @Nullable Instant voidedAt;

    @Column(name = "void_reason", length = 200)
    private @Nullable String voidReason;

    @Column(name = "calc_model_version", nullable = false, length = 40)
    private String calcModelVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_fill_id", nullable = false, updatable = false)
    @OrderBy("stageIndex ASC")
    private List<FillStageEntity> stages = new ArrayList<>();

    public FillEntity() {}

    public FillEntity(final Long teamId, final Long recordedByMemberId) {
        this.teamId = teamId;
        this.recordedByMemberId = recordedByMemberId;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Instant getFilledAt() {
        return filledAt;
    }

    public void setFilledAt(final Instant filledAt) {
        this.filledAt = filledAt;
    }

    public Long getRecordedByMemberId() {
        return recordedByMemberId;
    }

    public @Nullable Long getTargetVesselId() {
        return targetVesselId;
    }

    public @Nullable Double getWaterVolumeLitres() {
        return waterVolumeLitres;
    }

    public @Nullable String getCylinderLabel() {
        return cylinderLabel;
    }

    public @Nullable Long getChargedMemberId() {
        return chargedMemberId;
    }

    /** Targets a diver's cylinder; clears the vessel target. */
    public void setCylinderTarget(
            final double waterVolumeLitres,
            final @Nullable String cylinderLabel,
            final Long chargedMemberId) {
        this.targetVesselId = null;
        this.waterVolumeLitres = waterVolumeLitres;
        this.cylinderLabel = cylinderLabel;
        this.chargedMemberId = chargedMemberId;
    }

    /** Targets a vessel (its water volume applies); clears the cylinder target. */
    public void setVesselTarget(final Long vesselId) {
        this.targetVesselId = vesselId;
        this.waterVolumeLitres = null;
        this.cylinderLabel = null;
        this.chargedMemberId = null;
    }

    public double getStartPressureBar() {
        return startPressureBar;
    }

    public void setStartPressureBar(final double startPressureBar) {
        this.startPressureBar = startPressureBar;
    }

    public GasMix getStartMix() {
        return new GasMix(startO2, startHe);
    }

    public void setStartMix(final GasMix mix) {
        this.startO2 = mix.o2();
        this.startHe = mix.he();
    }

    public double getEndPressureBar() {
        return endPressureBar;
    }

    public void setEndPressureBar(final double endPressureBar) {
        this.endPressureBar = endPressureBar;
    }

    /** Computed end mix (DOMAIN §5.4), not the analyser reading. */
    public GasMix getEndMix() {
        return new GasMix(endO2, endHe);
    }

    public void setEndMix(final GasMix mix) {
        this.endO2 = mix.o2();
        this.endHe = mix.he();
    }

    /** The analyser reading after the fill, if any. */
    public @Nullable GasMix getEndAnalysis() {
        return endAnalysisO2 == null || endAnalysisHe == null
                ? null
                : new GasMix(endAnalysisO2, endAnalysisHe);
    }

    public void setEndAnalysis(final @Nullable GasMix mix) {
        this.endAnalysisO2 = mix == null ? null : mix.o2();
        this.endAnalysisHe = mix == null ? null : mix.he();
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public void setNotes(final @Nullable String notes) {
        this.notes = notes;
    }

    public @Nullable Instant getVoidedAt() {
        return voidedAt;
    }

    public @Nullable String getVoidReason() {
        return voidReason;
    }

    public String getCalcModelVersion() {
        return calcModelVersion;
    }

    public void setCalcModelVersion(final String calcModelVersion) {
        this.calcModelVersion = calcModelVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<FillStageEntity> getStages() {
        return stages;
    }

    public boolean isVesselTarget() {
        return targetVesselId != null;
    }

    public boolean isVoided() {
        return voidedAt != null;
    }

    /** Voided time and reason are always set together (see the DB check). */
    public void markVoided(final Instant voidedAt, final String reason) {
        this.voidedAt = voidedAt;
        this.voidReason = reason;
    }

    /** The stages are read here, so load them with the fill. */
    public StoredFill toRecord() {
        return new StoredFill(
                id,
                recordedByMemberId,
                createdAt,
                updatedAt,
                voidedAt,
                voidReason,
                new FillContent(
                        filledAt,
                        target(),
                        startPressureBar,
                        getStartMix(),
                        endPressureBar,
                        getEndMix(),
                        getEndAnalysis(),
                        notes,
                        calcModelVersion,
                        stages.stream().map(FillStageEntity::toRecord).toList()));
    }

    private FillTarget target() {
        if (targetVesselId != null) {
            return new FillTarget.Vessel(targetVesselId, null);
        }
        return new FillTarget.Cylinder(
                Objects.requireNonNull(waterVolumeLitres),
                cylinderLabel,
                Objects.requireNonNull(chargedMemberId));
    }
}

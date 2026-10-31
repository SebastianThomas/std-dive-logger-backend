package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.model.scrubber.StoredCanister;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A shared canister of CO2 absorbent (DOMAIN §12), with its cost lines and uses. */
@Entity
@Table(name = "t_scrubber_canister")
@SuppressWarnings("NullAway.Init")
public class ScrubberCanisterEntity {

    private static final int FETCH_BATCH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_scrubber_canister_id", nullable = false)
    private Long id;

    @Column(name = "fk_team_id", nullable = false)
    private Long teamId;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "capacity_kg", nullable = false)
    private double capacityKg;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "emptied_at")
    private @Nullable Instant emptiedAt;

    @Column(name = "fk_created_by_member_id", nullable = false)
    private Long createdByMemberId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_scrubber_canister_id", nullable = false)
    @OrderBy("id")
    @BatchSize(size = FETCH_BATCH)
    private List<ScrubberCostLineEntity> costLines = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fk_scrubber_canister_id", nullable = false)
    @OrderBy("usedAt DESC, id DESC")
    @BatchSize(size = FETCH_BATCH)
    private List<ScrubberUseEntity> uses = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScrubberCanisterEntity() {}

    public ScrubberCanisterEntity(
            final Long teamId,
            final String label,
            final double capacityKg,
            final Instant openedAt,
            final Long createdByMemberId) {
        this.teamId = teamId;
        this.label = label;
        this.capacityKg = capacityKg;
        this.openedAt = openedAt;
        this.createdByMemberId = createdByMemberId;
    }

    public Long getId() {
        return id;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public boolean isEmptied() {
        return emptiedAt != null;
    }

    public void markEmptied(final @Nullable Instant emptiedAt) {
        this.emptiedAt = emptiedAt;
    }

    /** In use at {@code at}: its window is {@code [openedAt, emptiedAt)}, like a bank's. */
    public boolean isInUseAt(final Instant at) {
        return !at.isBefore(openedAt) && (emptiedAt == null || at.isBefore(emptiedAt));
    }

    public List<ScrubberCostLineEntity> getCostLines() {
        return costLines;
    }

    public List<ScrubberUseEntity> getUses() {
        return uses;
    }

    public StoredCanister toRecord() {
        return new StoredCanister(
                id,
                label,
                capacityKg,
                openedAt,
                emptiedAt,
                createdByMemberId,
                costLines.stream().map(ScrubberCostLineEntity::toRecord).toList(),
                uses.stream().map(ScrubberUseEntity::toRecord).toList());
    }
}

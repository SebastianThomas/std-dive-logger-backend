package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.WeightFeeling;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "t_dive_configuration")
@SuppressWarnings("NullAway.Init")
public class DiveConfigurationEntity {
    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "fk_suit_id")
    private SuitEntity suit;

    // Nullable — only meaningful when baseConfiguration is a CCR variant. Unlike suit, most
    // dives simply have none.
    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ccr_unit_id")
    private @Nullable CcrUnitEntity ccrUnit;

    @Column(name = "base_configuration")
    @Enumerated(EnumType.STRING)
    private BaseConfiguration baseConfiguration;

    @Column(name = "weight_kg")
    private @Nullable Double weightKg;

    @Column(name = "weight_feeling")
    private @Nullable WeightFeeling weightFeeling;

    // orphanRemoval so a cylinder dropped by update() below is actually deleted rather than left
    // behind as a duplicated, orphaned row pointing at this same configuration. This only works
    // because update() mutates this exact managed collection in place (clear() + addAll()) -
    // replacing the field with a brand-new List instance instead would detach Hibernate's
    // persistent collection wrapper from the entity, and orphanRemoval would fail at flush with
    // "A collection with orphan deletion was no longer referenced by the owning entity instance".
    @OneToMany(
            mappedBy = "configuration",
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    private List<DiveConfigurationCylinderEntity> cylinders;

    public DiveConfigurationEntity() {}

    public DiveConfigurationEntity(
            final DiveEntity dive,
            final SuitEntity suit,
            @Nullable final CcrUnitEntity ccrUnit,
            final DiveConfiguration configuration,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.suit = suit;
        this.ccrUnit = ccrUnit;
        this.baseConfiguration = configuration.base();
        this.weightKg = configuration.weight();
        this.weightFeeling = configuration.weightFeeling();
        this.cylinders =
                configuration.cylinders().stream()
                        .map(
                                c ->
                                        new DiveConfigurationCylinderEntity(
                                                this,
                                                getCylinderSizeEntity.apply(c.size()),
                                                c.startBar(),
                                                c.endBar(),
                                                c.notes(),
                                                c.gas(),
                                                c.role(),
                                                c.usageStart(),
                                                c.usageEnd()))
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    public DiveConfiguration toRecord() {
        return new DiveConfiguration(
                suit.toRecord(),
                baseConfiguration,
                weightKg,
                weightFeeling,
                cylinders.stream().map(DiveConfigurationCylinderEntity::toRecord).toList(),
                ccrUnit != null ? ccrUnit.toRecord() : null);
    }

    /**
     * Updates this entity's fields in-place so that Hibernate never sees two managed objects with
     * the same identifier in the same session.
     */
    public void update(
            final SuitEntity suit,
            @Nullable final CcrUnitEntity ccrUnit,
            final DiveConfiguration configuration,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.suit = suit;
        this.ccrUnit = ccrUnit;
        this.baseConfiguration = configuration.base();
        this.weightKg = configuration.weight();
        this.weightFeeling = configuration.weightFeeling();
        // Resolve every CylinderSizeEntity (a find-or-create that can itself flush) *before*
        // touching this.cylinders - clear()ing the managed collection and only then hitting a
        // flush-triggering lookup mid-stream leaves Hibernate mid-mutation on this exact
        // orphanRemoval collection, which fails the same way replacing the field outright would
        // (see the field's comment above). Building the full replacement list first means clear()
        // and addAll() below run back-to-back with nothing in between that could flush.
        final var newCylinders =
                configuration.cylinders().stream()
                        .map(
                                c ->
                                        new DiveConfigurationCylinderEntity(
                                                this,
                                                getCylinderSizeEntity.apply(c.size()),
                                                c.startBar(),
                                                c.endBar(),
                                                c.notes(),
                                                c.gas(),
                                                c.role(),
                                                c.usageStart(),
                                                c.usageEnd()))
                        .toList();
        this.cylinders.clear();
        this.cylinders.addAll(newCylinders);
    }

    public SuitEntity getSuitEntity() {
        return suit;
    }

    public BaseConfiguration getBaseConfiguration() {
        return baseConfiguration;
    }
}

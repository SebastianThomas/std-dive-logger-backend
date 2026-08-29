package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.gear.StandardCylinder;
import ch.sthomas.stddivelogger.model.dive.gear.SuitType;
import ch.sthomas.stddivelogger.model.dive.gear.WeightFeeling;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

import org.hibernate.annotations.BatchSize;
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

    // Nullable - most dives use no CCR unit at all, or just one. See secondaryCcrUnit below for
    // genuine dual-rebreather setups.
    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_ccr_unit_id")
    private @Nullable CcrUnitEntity ccrUnit;

    // A second, independent CCR unit for dual-rebreather setups - each unit's own mount position
    // (CcrUnitEntity.mountPosition) says how it's worn, so any combination is representable.
    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_secondary_ccr_unit_id")
    private @Nullable CcrUnitEntity secondaryCcrUnit;

    // Nullable - the diver's own rig (backmount/sidemount) is independent of CCR and never
    // guessed; null means "not specified".
    @Column(name = "base_configuration")
    @Enumerated(EnumType.STRING)
    private @Nullable BaseConfiguration baseConfiguration;

    @Column(name = "weight_kg")
    private @Nullable Double weightKg;

    @Column(name = "weight_feeling")
    private @Nullable WeightFeeling weightFeeling;

    // A suit type noted for this dive with no specific saved Suit behind it at all - see
    // DiveConfiguration.adHocSuitType's own doc comment for why (e.g. a one-off rental).
    @Column(name = "ad_hoc_suit_type")
    @Enumerated(EnumType.STRING)
    private @Nullable SuitType adHocSuitType;

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
    @BatchSize(size = 30)
    private List<DiveConfigurationCylinderEntity> cylinders;

    public DiveConfigurationEntity() {}

    public DiveConfigurationEntity(
            final DiveEntity dive,
            final SuitEntity suit,
            @Nullable final CcrUnitEntity ccrUnit,
            @Nullable final CcrUnitEntity secondaryCcrUnit,
            final DiveConfiguration configuration,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.suit = suit;
        this.ccrUnit = ccrUnit;
        this.secondaryCcrUnit = secondaryCcrUnit;
        this.baseConfiguration = configuration.base();
        this.weightKg = configuration.weight();
        this.weightFeeling = configuration.weightFeeling();
        this.adHocSuitType = configuration.adHocSuitType();
        this.cylinders =
                configuration.cylinders().stream()
                        .map(c -> toCylinderEntity(c, getCylinderSizeEntity))
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    // Fills a null material via StandardCylinder.inferMaterial so imported/legacy payloads always
    // land with a value.
    private DiveConfigurationCylinderEntity toCylinderEntity(
            final DiveConfigurationCylinder c,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        final var material =
                c.material() != null
                        ? c.material()
                        : StandardCylinder.inferMaterial(c.size().liters(), c.size().unit());
        return new DiveConfigurationCylinderEntity(
                this,
                getCylinderSizeEntity.apply(c.size()),
                material,
                c.startBar(),
                c.endBar(),
                c.notes(),
                c.gas(),
                c.role(),
                c.usageWindows());
    }

    public DiveConfiguration toRecord() {
        return new DiveConfiguration(
                suit.toRecord(),
                baseConfiguration,
                weightKg,
                weightFeeling,
                cylinders.stream().map(DiveConfigurationCylinderEntity::toRecord).toList(),
                ccrUnit != null ? ccrUnit.toRecord() : null,
                secondaryCcrUnit != null ? secondaryCcrUnit.toRecord() : null,
                adHocSuitType);
    }

    /**
     * Updates this entity's fields in-place so that Hibernate never sees two managed objects with
     * the same identifier in the same session.
     */
    public void update(
            final SuitEntity suit,
            @Nullable final CcrUnitEntity ccrUnit,
            @Nullable final CcrUnitEntity secondaryCcrUnit,
            final DiveConfiguration configuration,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.suit = suit;
        this.ccrUnit = ccrUnit;
        this.secondaryCcrUnit = secondaryCcrUnit;
        this.baseConfiguration = configuration.base();
        this.weightKg = configuration.weight();
        this.weightFeeling = configuration.weightFeeling();
        this.adHocSuitType = configuration.adHocSuitType();
        // Resolve every CylinderSizeEntity (a find-or-create that can itself flush) *before*
        // touching this.cylinders - clear()ing the managed collection and only then hitting a
        // flush-triggering lookup mid-stream leaves Hibernate mid-mutation on this exact
        // orphanRemoval collection, which fails the same way replacing the field outright would
        // (see the field's comment above). Building the full replacement list first means clear()
        // and addAll() below run back-to-back with nothing in between that could flush.
        final var newCylinders =
                configuration.cylinders().stream()
                        .map(c -> toCylinderEntity(c, getCylinderSizeEntity))
                        .toList();
        this.cylinders.clear();
        this.cylinders.addAll(newCylinders);
    }

    public SuitEntity getSuitEntity() {
        return suit;
    }

    public @Nullable CcrUnitEntity getCcrUnitEntity() {
        return ccrUnit;
    }

    public @Nullable CcrUnitEntity getSecondaryCcrUnitEntity() {
        return secondaryCcrUnit;
    }

    public @Nullable BaseConfiguration getBaseConfiguration() {
        return baseConfiguration;
    }
}

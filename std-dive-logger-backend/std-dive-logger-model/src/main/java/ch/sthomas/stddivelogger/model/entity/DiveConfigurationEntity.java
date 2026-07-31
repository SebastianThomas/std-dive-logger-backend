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

    // orphanRemoval matters here specifically because update() below replaces this whole list with
    // a fresh one of brand-new entities on every edit rather than mutating it in place - without
    // it, the old rows are simply abandoned (never deleted), still pointing at this same
    // configuration, so every edit to a dive's cylinders left the previous set behind as
    // duplicated, orphaned rows.
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
                                                c.notes()))
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
        this.cylinders =
                configuration.cylinders().stream()
                        .map(
                                c ->
                                        new DiveConfigurationCylinderEntity(
                                                this,
                                                getCylinderSizeEntity.apply(c.size()),
                                                c.startBar(),
                                                c.endBar(),
                                                c.notes()))
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    public SuitEntity getSuitEntity() {
        return suit;
    }

    public BaseConfiguration getBaseConfiguration() {
        return baseConfiguration;
    }
}

package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.WeightFeeling;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

import java.util.List;
import java.util.function.Function;

@Entity
@Table(name = "t_dive_configuration")
public class DiveConfigurationEntity {
    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "fk_suit_id")
    private SuitEntity suit;

    @Column(name = "base_configuration")
    @Enumerated(EnumType.STRING)
    private BaseConfiguration baseConfiguration;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "weight_feeling")
    private WeightFeeling weightFeeling;

    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DiveConfigurationCylinderEntity> cylinders;

    public DiveConfigurationEntity() {}

    public DiveConfigurationEntity(
            final DiveEntity dive,
            final DiveConfiguration configuration,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.suit = configuration.suit() == null ? null : new SuitEntity(configuration.suit());
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
                        .toList();
    }

    public DiveConfiguration toRecord() {
        return new DiveConfiguration(
                suit.toRecord(),
                baseConfiguration,
                weightKg,
                weightFeeling,
                cylinders.stream().map(DiveConfigurationCylinderEntity::toRecord).toList());
    }
}

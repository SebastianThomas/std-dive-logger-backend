package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_gas_consumption")
public class DiveGasConsumptionEntity {
    @Id
    @Column(name = "fk_dive_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @Column(name = "sac_bar")
    private double sacBar;

    @Column(name = "rmv_liters")
    private double rmvLiters;

    @Column(name = "total_liters")
    private double totalLiters;

    public DiveGasConsumptionEntity() {}

    public DiveGasConsumptionEntity(
            final DiveEntity dive, final DiveGasConsumption gasConsumption) {
        this(
                dive,
                gasConsumption.sacBar(),
                gasConsumption.rmvLiters(),
                gasConsumption.totalLiters());
    }

    public DiveGasConsumptionEntity(
            final DiveEntity dive,
            final Double sacBar,
            final Double rmvLiters,
            final Double totalLiters) {
        this.diveId = dive.getId();
        this.dive = dive;
        this.sacBar = sacBar;
        this.rmvLiters = rmvLiters;
        this.totalLiters = totalLiters;
    }

    public DiveGasConsumption toRecord() {
        return new DiveGasConsumption(sacBar, rmvLiters, totalLiters);
    }
}

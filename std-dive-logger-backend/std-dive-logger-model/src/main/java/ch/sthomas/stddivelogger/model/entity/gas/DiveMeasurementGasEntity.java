package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;

import jakarta.persistence.*;

/**
 * The backend's own computed PO2/FO2 for one measurement - see {@link
 * ch.sthomas.stddivelogger.model.analytics.DiveProfileGasResponse} for what this is (and isn't: not
 * the same thing as {@code PO2Entity.calculated}, which is a source device's own onboard value).
 */
@Entity
@Table(name = "t_dive_measurement_gas")
@SuppressWarnings("NullAway.Init")
public class DiveMeasurementGasEntity {
    @Id
    @Column(name = "fk_dive_measurement_id")
    private Long measurementId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_dive_measurement_id")
    private DiveMeasurementEntity measurement;

    @Column(name = "calculated_po2", nullable = false)
    private double calculatedPo2;

    @Column(name = "calculated_fo2", nullable = false)
    private double calculatedFo2;

    public DiveMeasurementGasEntity() {}

    public DiveMeasurementGasEntity(
            final DiveMeasurementEntity measurement,
            final double calculatedPo2,
            final double calculatedFo2) {
        this.measurement = measurement;
        this.calculatedPo2 = calculatedPo2;
        this.calculatedFo2 = calculatedFo2;
    }

    public DiveMeasurementEntity getMeasurement() {
        return measurement;
    }

    public double getCalculatedPo2() {
        return calculatedPo2;
    }

    public double getCalculatedFo2() {
        return calculatedFo2;
    }
}

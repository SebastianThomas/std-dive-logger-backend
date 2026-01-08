package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.entity.DiveMeasurementEntity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

@Entity
@Table(name = "t_measurement_po2")
public class PO2Entity {
    @Id
    @Column(name = "fk_measurement_id")
    private Long diveId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "fk_measurement_id")
    private DiveMeasurementEntity measurement;

    @Column(name = "max_set_point")
    @Nullable
    Double maxSetPoint;

    @Column(name = "measured")
    @Nullable
    Double measured;

    @Column(name = "calculated")
    @Nullable
    Double calculated;

    public PO2Entity() {}

    public PO2Entity(final PO2 record) {
        maxSetPoint = record.maxSetPoint();
        measured = record.measured();
        calculated = record.calculated();
    }

    public PO2 toRecord() {
        return new PO2(maxSetPoint, measured, calculated);
    }
}

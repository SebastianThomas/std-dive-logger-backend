package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.entity.converter.DecoStopsToStringConverter;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "t_dive_measurements")
public class DiveMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_measurement_id", nullable = false)
    private Long id;

    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Column(name = "depth", nullable = false)
    private double depth;

    @Column(name = "temperature_celsius", nullable = false)
    private Double temperatureCelsius;

    @Column(name = "rmv_liters", nullable = true)
    private Double rmv;

    @Column(name = "n2", nullable = true)
    private Double n2;

    @Column(name = "o2_tox", nullable = true)
    private Double o2Tox;

    @Column(name = "cns", nullable = true)
    private Double cns;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = DecoStopsToStringConverter.class)
    @Column(name = "deco_stops")
    private List<DecoStop> decoStops;

    @Column(name = "ndl_minutes", nullable = false)
    private Integer ndlMinutes;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_gas_id")
    private GasEntity gas;

    @JoinColumn(name = "fk_dive_profile_id")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private DiveProfileEntity profile;

    public DiveMeasurementEntity() {}

    public DiveMeasurementEntity(
            final DiveMeasurement diveMeasurement, @Nullable final GasEntity gas) {
        this.time = diveMeasurement.time().atOffset(UTC);
        this.depth = diveMeasurement.depth();
        this.temperatureCelsius = diveMeasurement.temperature().celsius();
        this.ndlMinutes = (int) diveMeasurement.ndl().toMinutes();
        this.gas = gas;
        this.rmv = diveMeasurement.rmvLiters();
        this.n2 = diveMeasurement.n2();
        this.o2Tox = diveMeasurement.o2Tox();
        this.cns = diveMeasurement.cns();
    }

    public DiveMeasurementEntity(
            final DiveMeasurementWithId diveMeasurementWithId, @Nullable final GasEntity gas) {
        this(diveMeasurementWithId.measurement(), gas);
        this.id = diveMeasurementWithId.id();
    }

    public DiveMeasurement toRecord() {
        return new DiveMeasurement(
                time.toInstant(),
                new Temperature(temperatureCelsius, Temperature.TemperatureUnit.CELSIUS),
                depth,
                Duration.ofMinutes(ndlMinutes),
                decoStops,
                Optional.ofNullable(gas).map(GasEntity::toRecord).orElse(null),
                rmv,
                n2,
                o2Tox,
                cns);
    }

    public DiveMeasurementWithId toRecordWithId() {
        return new DiveMeasurementWithId(toRecord(), id);
    }

    public DiveMeasurementEntity setProfile(final DiveProfileEntity diveProfileEntity) {
        this.profile = diveProfileEntity;
        return this;
    }
}

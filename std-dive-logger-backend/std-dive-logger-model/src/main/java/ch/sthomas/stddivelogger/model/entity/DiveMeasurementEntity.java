package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.entity.converter.DecoStopsToStringConverter;
import ch.sthomas.stddivelogger.model.entity.gas.GasEntity;
import ch.sthomas.stddivelogger.model.entity.gas.PO2Entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "t_dive_measurements")
@SuppressWarnings("NullAway.Init")
public class DiveMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_measurement_id", nullable = false)
    private Long id;

    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Column(name = "depth", nullable = false)
    private double depth;

    @Column(name = "temperature_celsius", nullable = true)
    private @Nullable Double temperatureCelsius;

    @OneToOne(
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            optional = true,
            mappedBy = "measurement")
    private @Nullable PO2Entity po2;

    @Column(name = "rmv_liters", nullable = true)
    private @Nullable Double rmv;

    @Column(name = "n2", nullable = true)
    private @Nullable Double n2;

    @Column(name = "o2_tox", nullable = true)
    private @Nullable Double o2Tox;

    @Column(name = "cns", nullable = true)
    private @Nullable Double cns;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = true)
    private @Nullable DiveMode mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = DecoStopsToStringConverter.class)
    @Column(name = "deco_stops")
    private List<DecoStop> decoStops;

    @Column(name = "ndl_minutes", nullable = true)
    private @Nullable Integer ndlMinutes;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "fk_gas_id")
    private @Nullable GasEntity gas;

    @JoinColumn(name = "fk_dive_profile_id")
    @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    private DiveProfileEntity profile;

    public DiveMeasurementEntity() {}

    public DiveMeasurementEntity(
            final DiveMeasurement diveMeasurement, @Nullable final GasEntity gas) {
        this.time = diveMeasurement.time().atOffset(UTC);
        this.depth = diveMeasurement.depth();
        this.temperatureCelsius =
                Optional.ofNullable(diveMeasurement.temperature())
                        .map(Temperature::celsius)
                        .orElse(null);
        this.ndlMinutes =
                Optional.ofNullable(diveMeasurement.ndl())
                        .map(Duration::toMinutes)
                        .map(Long::intValue)
                        .orElse(null);
        this.gas = gas;
        this.rmv = diveMeasurement.rmvLiters();
        this.n2 = diveMeasurement.n2();
        this.o2Tox = diveMeasurement.o2Tox();
        this.cns = diveMeasurement.cns();
        this.mode = diveMeasurement.mode();
        this.decoStops = Optional.ofNullable(diveMeasurement.deco()).orElse(List.of());
        this.po2 =
                Optional.ofNullable(diveMeasurement.po2())
                        .map(p -> new PO2Entity(p, this))
                        .orElse(null);
    }

    public DiveMeasurementEntity(
            final DiveMeasurementWithId diveMeasurementWithId, @Nullable final GasEntity gas) {
        this(diveMeasurementWithId.measurement(), gas);
        this.id = diveMeasurementWithId.id();
    }

    public DiveMeasurement toRecord() {
        return new DiveMeasurement(
                time.toInstant(),
                Optional.ofNullable(temperatureCelsius)
                        .map(t -> new Temperature(t, Temperature.TemperatureUnit.CELSIUS))
                        .orElse(null),
                depth,
                Optional.ofNullable(ndlMinutes).map(Duration::ofMinutes).orElse(null),
                decoStops,
                Optional.ofNullable(gas).map(GasEntity::toRecord).orElse(null),
                Optional.ofNullable(po2).map(PO2Entity::toRecord).orElse(null),
                rmv,
                n2,
                o2Tox,
                cns,
                mode);
    }

    public DiveMeasurementWithId toRecordWithId() {
        return new DiveMeasurementWithId(toRecord(), id);
    }

    public DiveMeasurementEntity setProfile(final DiveProfileEntity diveProfileEntity) {
        this.profile = diveProfileEntity;
        return this;
    }

    public Long getId() {
        return id;
    }

    public List<DecoStop> getDecoStops() {
        return decoStops;
    }

    public double getDepth() {
        return depth;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public void timePlus(final Duration diff) {
        this.time = time.plus(diff);
    }
}

package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.DecoStop;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;
import ch.sthomas.stddivelogger.model.entity.converter.DecoStopsToStringConverter;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = DecoStopsToStringConverter.class)
    @Column(name = "deco_stops")
    private List<DecoStop> decoStops;

    @Column(name = "ndl_minutes", nullable = false)
    private Integer ndlMinutes;

    @JoinColumn(name = "fk_dive_profile_id")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private DiveProfileEntity profile;

    public DiveMeasurementEntity() {}

    public DiveMeasurementEntity(final DiveMeasurement diveMeasurement) {
        this.time = diveMeasurement.time().atOffset(UTC);
        this.depth = diveMeasurement.depth();
        this.temperatureCelsius = diveMeasurement.temperature().celsius();
        this.ndlMinutes = (int) diveMeasurement.ndl().toMinutes();
    }

    public DiveMeasurementEntity(final DiveMeasurementWithId diveMeasurementWithId) {
        this(diveMeasurementWithId.measurement());
        this.id = diveMeasurementWithId.id();
    }

    public DiveMeasurement toRecord() {
        return new DiveMeasurement(
                time.toInstant(),
                new Temperature(temperatureCelsius, Temperature.TemperatureUnit.CELSIUS),
                depth,
                Duration.ofMinutes(ndlMinutes),
                decoStops,
                null);
    }

    public DiveMeasurementWithId toRecordWithId() {
        return new DiveMeasurementWithId(toRecord(), id);
    }

    public DiveMeasurementEntity setProfile(final DiveProfileEntity diveProfileEntity) {
        this.profile = diveProfileEntity;
        return this;
    }
}

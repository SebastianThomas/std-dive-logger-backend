package ch.sthomas.stddivelogger.model.entity;

import static java.time.ZoneOffset.UTC;

import ch.sthomas.stddivelogger.model.dive.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.measurement.Temperature;

import jakarta.persistence.*;

import java.time.Duration;
import java.time.ZonedDateTime;

@Entity
@Table(name = "t_dive_measurements")
public class DiveMeasurementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_measurement_id", nullable = false)
    private Long id;

    @Column(name = "time", nullable = false)
    private ZonedDateTime time;

    @Column(name = "depth", nullable = false)
    private double depth;

    @Column(name = "temperature_celsius", nullable = false)
    private Double temperatureCelsius;

    @Column(name = "ndl_minutes", nullable = false)
    private Integer ndlMinutes;

    public DiveMeasurementEntity() {}

    public DiveMeasurementEntity(final DiveMeasurement diveMeasurement) {
        this.time = diveMeasurement.time().atZone(UTC);
        this.depth = diveMeasurement.depth();
        this.temperatureCelsius = diveMeasurement.temperature().celsius();
        this.ndlMinutes = (int) diveMeasurement.ndl().toMinutes();
    }

    public DiveMeasurement toRecord() {
        return new DiveMeasurement(
                time.toInstant(),
                new Temperature(temperatureCelsius, Temperature.TemperatureUnit.CELSIUS),
                depth,
                Duration.ofMinutes(ndlMinutes),
                null);
    }
}

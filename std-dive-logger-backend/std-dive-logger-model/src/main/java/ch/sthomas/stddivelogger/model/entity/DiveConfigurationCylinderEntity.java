package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_dive_configuration_cylinder")
@SuppressWarnings("NullAway.Init")
public class DiveConfigurationCylinderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_configuration_cylinder_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_dive_id")
    private DiveConfigurationEntity configuration;

    @ManyToOne
    @JoinColumn(name = "fk_cylinder_size_id", nullable = false)
    private CylinderSizeEntity cylinderSize;

    @Column(name = "start_bar")
    private @Nullable Double startBar;

    @Column(name = "end_bar")
    private @Nullable Double endBar;

    @Column(name = "notes", nullable = false)
    private String notes;

    @Column(name = "gas_o2", nullable = false)
    private double gasO2;

    @Column(name = "gas_he", nullable = false)
    private double gasHe;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private CylinderRole role;

    @Column(name = "usage_start")
    private @Nullable Instant usageStart;

    @Column(name = "usage_end")
    private @Nullable Instant usageEnd;

    public DiveConfigurationCylinderEntity() {}

    public DiveConfigurationCylinderEntity(
            final DiveConfigurationEntity configuration,
            final CylinderSizeEntity cylinderSize,
            @Nullable final Double startBar,
            @Nullable final Double endBar,
            final String notes,
            final Gas gas,
            final CylinderRole role,
            @Nullable final Instant usageStart,
            @Nullable final Instant usageEnd) {
        this.configuration = configuration;
        this.cylinderSize = cylinderSize;
        this.startBar = startBar;
        this.endBar = endBar;
        this.notes = notes;
        this.gasO2 = gas.o2();
        this.gasHe = gas.he();
        this.role = role;
        this.usageStart = usageStart;
        this.usageEnd = usageEnd;
    }

    public DiveConfigurationCylinder toRecord() {
        return new DiveConfigurationCylinder(
                id,
                cylinderSize.toRecord(),
                startBar,
                endBar,
                notes,
                new Gas(gasO2, gasHe),
                role,
                usageStart,
                usageEnd);
    }
}

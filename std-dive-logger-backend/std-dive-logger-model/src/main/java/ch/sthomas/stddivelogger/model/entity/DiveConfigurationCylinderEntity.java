package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderMaterial;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.CylinderUsageWindow;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    // Descriptive only. Nullable - a truly unknown legacy row may stay null; new writes always get
    // a
    // value from DiveConfigurationEntity (explicit or inferred).
    @Column(name = "material")
    @Enumerated(EnumType.STRING)
    private @Nullable CylinderMaterial material;

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

    // Ordered list of the stretches this cylinder was breathed over - empty means "the complement
    // of the same-role windowed cylinders". See DiveConfigurationCylinder /
    // CylinderConsumptionCalculator.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "t_dive_configuration_cylinder_usage_window",
            joinColumns = @JoinColumn(name = "fk_configuration_cylinder_id"))
    @OrderColumn(name = "sort_order")
    private List<CylinderUsageWindowEmbeddable> usageWindows;

    public DiveConfigurationCylinderEntity() {}

    public DiveConfigurationCylinderEntity(
            final DiveConfigurationEntity configuration,
            final CylinderSizeEntity cylinderSize,
            @Nullable final CylinderMaterial material,
            @Nullable final Double startBar,
            @Nullable final Double endBar,
            final String notes,
            final Gas gas,
            final CylinderRole role,
            final List<CylinderUsageWindow> usageWindows) {
        this.configuration = configuration;
        this.cylinderSize = cylinderSize;
        this.material = material;
        this.startBar = startBar;
        this.endBar = endBar;
        this.notes = notes;
        this.gasO2 = gas.o2();
        this.gasHe = gas.he();
        this.role = role;
        this.usageWindows =
                usageWindows.stream()
                        .map(CylinderUsageWindowEmbeddable::new)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Shifts every timed usage window by {@code delta} - for re-dating the whole dive. */
    public void shiftUsageWindowsBy(final java.time.Duration delta) {
        usageWindows.forEach(w -> w.shiftBy(delta));
    }

    public DiveConfigurationCylinder toRecord() {
        return new DiveConfigurationCylinder(
                id,
                cylinderSize.toRecord(),
                material,
                startBar,
                endBar,
                notes,
                new Gas(gasO2, gasHe),
                role,
                usageWindows.stream().map(CylinderUsageWindowEmbeddable::toRecord).toList());
    }
}

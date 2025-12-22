package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_configuration_cylinder")
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
    private Double startBar;

    @Column(name = "end_bar")
    private Double endBar;

    @Column(name = "notes", nullable = false)
    private String notes;

    public DiveConfigurationCylinderEntity() {}

    public DiveConfigurationCylinderEntity(
            final DiveConfigurationEntity configuration,
            final CylinderSizeEntity cylinderSize,
            final Double startBar,
            final Double endBar,
            final String notes) {
        this.configuration = configuration;
        this.cylinderSize = cylinderSize;
        this.startBar = startBar;
        this.endBar = endBar;
        this.notes = notes;
    }

    public DiveConfigurationCylinder toRecord() {
        return new DiveConfigurationCylinder(id, cylinderSize.toRecord(), startBar, endBar, notes);
    }
}

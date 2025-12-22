package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

@Entity
@Table(
        name = "t_cylinder_size",
        uniqueConstraints = @UniqueConstraint(columnNames = {"unit", "value"}))
public class CylinderSizeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_cylinder_size_id")
    private Integer id;

    @Column(name = "unit", nullable = false)
    @Enumerated(EnumType.STRING)
    private CylinderSizeUnit unit;

    @Column(name = "value", nullable = false)
    private Double value;

    @OneToMany(mappedBy = "cylinderSize")
    public Set<GasEntity> gases;

    public CylinderSizeEntity() {}

    public CylinderSizeEntity(final @NotNull CylinderSize size) {
        this.unit = size.unit();
        this.value = size.value();
    }

    public CylinderSize toRecord() {
        return new CylinderSize(unit, value);
    }
}

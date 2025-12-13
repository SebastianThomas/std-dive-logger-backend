package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.measurement.CylinderSizeUnit;

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
    public Integer id;

    @Column(name = "unit", nullable = false)
    @Enumerated(EnumType.STRING)
    public CylinderSizeUnit unit;

    @Column(name = "value", nullable = false)
    public Double value;

    @OneToMany(mappedBy = "cylinderSize")
    public Set<GasEntity> gases;

    public CylinderSizeEntity() {}

    public CylinderSizeEntity(final @NotNull CylinderSize size) {
        this.unit = size.unit();
        this.value = size.value();
    }
}

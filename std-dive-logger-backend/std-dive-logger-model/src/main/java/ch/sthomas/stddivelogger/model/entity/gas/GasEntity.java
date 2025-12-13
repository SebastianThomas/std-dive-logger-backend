package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.measurement.GasContent;
import ch.sthomas.stddivelogger.model.dive.measurement.GasContentUnit;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.util.Optional;

@Entity
@Table(name = "t_gas")
public class GasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_gas_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_gas_mix_id", nullable = false)
    public GasMixEntity gasMix;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_cylinder_size_id", nullable = false)
    public CylinderSizeEntity cylinderSize;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    public String description;

    @Column(name = "content_value")
    public Double contentValue;

    @Column(name = "content_unit")
    @Enumerated(EnumType.STRING)
    public GasContentUnit contentUnit;

    public GasEntity() {}

    public GasEntity(
            final Gas gas,
            final GasMixEntity gasMix,
            @Nullable final CylinderSizeEntity cylinderSize) {
        this.contentUnit = Optional.ofNullable(gas.content()).map(GasContent::unit).orElse(null);
        this.contentValue = Optional.ofNullable(gas.content()).map(GasContent::value).orElse(null);
        this.description = gas.description();
        this.gasMix = gasMix;
        this.cylinderSize = cylinderSize;
    }

    public Gas toRecord() {
        return new Gas(
                gasMix.o2,
                gasMix.n2,
                gasMix.he,
                0.0,
                cylinderSize != null
                        ? new CylinderSize(cylinderSize.unit, cylinderSize.value)
                        : null,
                contentUnit != null && contentValue != null
                        ? new GasContent(contentUnit, contentValue)
                        : null,
                description);
    }
}

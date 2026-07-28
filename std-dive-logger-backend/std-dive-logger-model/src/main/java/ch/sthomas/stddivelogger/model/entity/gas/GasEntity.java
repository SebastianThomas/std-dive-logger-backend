package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.GasContent;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.GasContentUnit;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

@Entity
@Table(name = "t_gas")
@SuppressWarnings("NullAway.Init")
public class GasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_gas_id")
    public Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_gas_mix_id", nullable = false)
    public GasMixEntity gasMix;

    @ManyToOne(optional = false)
    @JoinColumn(name = "fk_cylinder_size_id")
    public @Nullable CylinderSizeEntity cylinderSize;

    @Column(name = "description", columnDefinition = "TEXT")
    public @Nullable String description;

    @Column(name = "content_value")
    public @Nullable Double contentValue;

    @Column(name = "content_unit")
    @Enumerated(EnumType.STRING)
    public @Nullable GasContentUnit contentUnit;

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
                cylinderSize != null ? cylinderSize.toRecord() : null,
                contentUnit != null && contentValue != null
                        ? new GasContent(contentUnit, contentValue)
                        : null,
                description);
    }
}

package ch.sthomas.stddivelogger.model.entity.gas;

import ch.sthomas.stddivelogger.model.dive.measurement.Gas;

import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "t_gas_mix", uniqueConstraints = @UniqueConstraint(columnNames = {"o2", "n2", "he"}))
public class GasMixEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_gas_mix_id")
    public Long id;

    @Column(name = "o2", nullable = false)
    public Double o2;

    @Column(name = "n2", nullable = false)
    public Double n2;

    @Column(name = "he", nullable = false)
    public Double he = 0.0;

    @OneToMany(mappedBy = "gasMix")
    public Set<GasEntity> gases;

    public GasMixEntity() {}

    public GasMixEntity(final double o2, final double n2, final double he) {
        this.o2 = o2;
        this.n2 = n2;
        this.he = he;
    }

    public GasMixEntity(final Gas gas) {
        this.o2 = gas.o2();
        this.n2 = gas.n2();
        this.he = gas.he();
    }
}

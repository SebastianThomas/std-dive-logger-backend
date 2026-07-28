package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;

import jakarta.persistence.*;

@Entity
@Table(name = "t_computer_manufacturer")
@SuppressWarnings("NullAway.Init")
public class DiveComputerManufacturerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_manufacturer_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    public DiveComputerManufacturerEntity() {}

    public DiveComputerManufacturerEntity(final String name) {
        this.name = name;
    }

    public DiveComputerManufacturerEntity(final long id, final String name) {
        this.id = id;
        this.name = name;
    }

    public DiveComputerManufacturer toRecord() {
        return new DiveComputerManufacturer(id, name);
    }
}

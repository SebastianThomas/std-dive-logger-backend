package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.DiveComputer;

import jakarta.persistence.*;

@Entity
@Table(name = "t_dive_computer")
public class DiveComputerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_computer_id", nullable = false)
    private Long id;

    @Column(name = "custom_identifier", nullable = false)
    private String customIdentifier;

    public DiveComputer toRecord() {
        return new DiveComputer(id, customIdentifier);
    }
}

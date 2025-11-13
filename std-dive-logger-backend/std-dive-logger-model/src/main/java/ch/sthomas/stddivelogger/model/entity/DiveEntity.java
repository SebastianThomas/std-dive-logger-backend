package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.Dive;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "t_dives")
public class DiveEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_id", nullable = false)
    private Long id;

    @Column(name = "dive_number", nullable = false)
    private int number;

    @Column(name = "dive_identifier")
    private String diveIdentifier;

    @ManyToOne private UserEntity user;

    @ManyToOne private DiveSiteEntity diveSite;

    @OneToMany private List<DiveProfileEntity> profiles;

    public DiveEntity() {}

    public DiveEntity(
            final int number,
            final String diveIdentifier,
            final UserEntity userEntity,
            final DiveSiteEntity diveSiteEntity,
            final List<DiveProfileEntity> profiles) {
        this.number = number;
        this.diveIdentifier = diveIdentifier;
        this.user = userEntity;
        this.diveSite = diveSiteEntity;
        this.profiles = profiles;
    }

    public Dive toRecord() {
        return new Dive(
                id,
                number,
                diveIdentifier,
                diveSite.toRecord(),
                profiles.stream().map(DiveProfileEntity::toRecord).toList());
    }
}

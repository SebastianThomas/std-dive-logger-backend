package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTrip;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripType;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Entity
@Table(name = "t_dive_trip")
@SuppressWarnings("NullAway.Init")
public class DiveTripEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_trip_id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DiveTripType type;

    @ManyToOne
    @JoinColumn(name = "fk_owner_user_id", nullable = false)
    private UserEntity owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_terminology")
    private @Nullable TeamTerminology teamTerminology;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DiveTripEntity() {}

    public DiveTripEntity(final String name, final DiveTripType type, final UserEntity owner) {
        this.name = name;
        this.type = type;
        this.owner = owner;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setType(final DiveTripType type) {
        this.type = type;
    }

    public void setTeamTerminology(@Nullable final TeamTerminology teamTerminology) {
        this.teamTerminology = teamTerminology;
    }

    public DiveTrip toRecord() {
        return new DiveTrip(id, name, type, owner.getId(), teamTerminology, createdAt);
    }
}

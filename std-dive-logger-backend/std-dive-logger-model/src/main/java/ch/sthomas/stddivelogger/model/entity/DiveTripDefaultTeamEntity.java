package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripDefaultTeamMember;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_trip_default_team")
@SuppressWarnings("NullAway.Init")
public class DiveTripDefaultTeamEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_trip_id", nullable = false)
    private DiveTripEntity trip;

    @ManyToOne
    @JoinColumn(name = "fk_buddy_user_id")
    private @Nullable UserEntity buddyUser;

    @Column(name = "buddy_name")
    private @Nullable String buddyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private BuddyRole role;

    public DiveTripDefaultTeamEntity() {}

    public DiveTripDefaultTeamEntity(
            final DiveTripEntity trip,
            @Nullable final UserEntity buddyUser,
            @Nullable final String buddyName,
            final BuddyRole role) {
        this.trip = trip;
        this.buddyUser = buddyUser;
        this.buddyName = buddyName;
        this.role = role;
    }

    public DiveTripDefaultTeamMember toRecord() {
        return new DiveTripDefaultTeamMember(
                id,
                buddyUser != null ? buddyUser.toRecord().toFrontendModel() : null,
                buddyName,
                role);
    }
}

package ch.sthomas.stddivelogger.model.entity;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "t_dive_trip_member")
@SuppressWarnings("NullAway.Init")
public class DiveTripMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "fk_trip_id", nullable = false)
    private DiveTripEntity trip;

    @ManyToOne
    @JoinColumn(name = "fk_member_dive_id")
    private @Nullable DiveEntity memberDive;

    @ManyToOne
    @JoinColumn(name = "fk_member_trip_id")
    private @Nullable DiveTripEntity memberTrip;

    public DiveTripMemberEntity() {}

    public static DiveTripMemberEntity forDive(
            final DiveTripEntity trip, final DiveEntity memberDive) {
        final var entity = new DiveTripMemberEntity();
        entity.trip = trip;
        entity.memberDive = memberDive;
        return entity;
    }

    public static DiveTripMemberEntity forTrip(
            final DiveTripEntity trip, final DiveTripEntity memberTrip) {
        final var entity = new DiveTripMemberEntity();
        entity.trip = trip;
        entity.memberTrip = memberTrip;
        return entity;
    }

    public DiveTripEntity getTrip() {
        return trip;
    }

    public @Nullable DiveEntity getMemberDive() {
        return memberDive;
    }

    public @Nullable DiveTripEntity getMemberTrip() {
        return memberTrip;
    }
}

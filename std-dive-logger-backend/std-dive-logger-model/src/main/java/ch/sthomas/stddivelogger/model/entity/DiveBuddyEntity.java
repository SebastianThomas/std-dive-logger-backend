package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.BuddyRole;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

/**
 * A linked-dive buddy pair - symmetric in storage (the DB {@code CHECK fk_dive_id <
 * fk_buddy_dive_id} means {@code dive} is always the lower-id side, {@code buddyDive} the higher-id
 * side), but role is inherently directional: each side can rate the other differently, hence the
 * two separate role columns rather than one. Use {@link #roleAsSeenFrom} / {@link
 * #setRoleAsSeenFrom} rather than the raw getters/setters when the caller only has "which of the
 * two dives am I looking from" rather than "which one is the DB's lower-id side".
 */
@Entity
@Table(name = "t_dive_buddy")
@SuppressWarnings("NullAway.Init")
public class DiveBuddyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_buddy_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_dive_id")
    private DiveEntity dive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_buddy_dive_id")
    private DiveEntity buddyDive;

    /** Role of {@code buddyDive}'s diver, as rated from {@code dive}'s side. */
    @Column(name = "role_of_buddy_from_dive")
    @Enumerated(EnumType.STRING)
    private @Nullable BuddyRole roleOfBuddyFromDive;

    /** Role of {@code dive}'s diver, as rated from {@code buddyDive}'s side. */
    @Column(name = "role_of_dive_from_buddy")
    @Enumerated(EnumType.STRING)
    private @Nullable BuddyRole roleOfDiveFromBuddy;

    public DiveBuddyEntity() {}

    /**
     * {@code dive} must already be the lower-id side and {@code buddyDive} the higher-id side -
     * callers that don't know the ordering up front should determine it first (see {@code
     * DiveDataService.linkDive}).
     */
    public DiveBuddyEntity(final DiveEntity dive, final DiveEntity buddyDive) {
        this.dive = dive;
        this.buddyDive = buddyDive;
    }

    public Long getId() {
        return id;
    }

    public DiveEntity getDive() {
        return dive;
    }

    public DiveEntity getBuddyDive() {
        return buddyDive;
    }

    public @Nullable BuddyRole getRoleOfBuddyFromDive() {
        return roleOfBuddyFromDive;
    }

    public @Nullable BuddyRole getRoleOfDiveFromBuddy() {
        return roleOfDiveFromBuddy;
    }

    /** The other diver's role, as rated from {@code viewpointDiveId}'s side of this link. */
    public @Nullable BuddyRole roleAsSeenFrom(final long viewpointDiveId) {
        return dive.getId() == viewpointDiveId ? roleOfBuddyFromDive : roleOfDiveFromBuddy;
    }

    /** Sets the other diver's role, as rated from {@code viewpointDiveId}'s side of this link. */
    public void setRoleAsSeenFrom(final long viewpointDiveId, @Nullable final BuddyRole role) {
        if (dive.getId() == viewpointDiveId) {
            this.roleOfBuddyFromDive = role;
        } else {
            this.roleOfDiveFromBuddy = role;
        }
    }
}

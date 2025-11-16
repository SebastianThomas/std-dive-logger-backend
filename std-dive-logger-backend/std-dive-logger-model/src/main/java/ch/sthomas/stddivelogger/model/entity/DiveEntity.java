package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.Dive;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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

    @ManyToMany
    @JoinTable(
            name = "t_dive_buddy",
            joinColumns = @JoinColumn(name = "fk_dive_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_buddy_dive_id"))
    private List<DiveEntity> buddyDivesFrom;

    @ManyToMany(mappedBy = "buddyDivesFrom")
    private List<DiveEntity> buddyDivesTo;

    @OneToMany(mappedBy = "dive", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DiveBuddyNameEntity> namedBuddies;

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
                profiles.stream().map(DiveProfileEntity::toRecord).toList(),
                Stream.concat(buddyDivesFrom.stream(), buddyDivesTo.stream())
                        .map(DiveEntity::toRecord)
                        .toList(),
                namedBuddies.stream().map(DiveBuddyNameEntity::getName).toList());
    }

    public UserEntity getUserEntity() {
        return user;
    }

    public DiveEntity update(
            final int number,
            final String diveIdentifier,
            @Nullable final DiveSiteEntity diveSiteEntity) {
        this.number = number;
        this.diveIdentifier = diveIdentifier;
        if (diveSiteEntity != null) {
            this.diveSite = diveSiteEntity;
        }
        return this;
    }

    public List<DiveProfileEntity> getProfiles() {
        return profiles;
    }

    public void addProfiles(final List<DiveProfileEntity> profiles) {
        this.profiles = new ArrayList<>(this.profiles);
        this.profiles.addAll(profiles);
    }
}

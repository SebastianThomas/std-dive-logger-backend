package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.dive.BuddyDive;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.SimplifiedDive;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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

    @Column(name = "preview_image", nullable = false)
    private String previewImage;

    @JoinColumn(name = "fk_diver_id")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private UserEntity user;

    @JoinColumn(name = "dive_site")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private DiveSiteEntity diveSite;

    @OneToMany(mappedBy = "dive", cascade = CascadeType.PERSIST)
    private List<DiveProfileEntity> profiles;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "t_dive_buddy",
            joinColumns = @JoinColumn(name = "fk_dive_id"),
            inverseJoinColumns = @JoinColumn(name = "fk_buddy_dive_id"))
    private List<DiveEntity> buddyDivesFrom;

    @ManyToMany(mappedBy = "buddyDivesFrom", fetch = FetchType.EAGER)
    private List<DiveEntity> buddyDivesTo;

    @OneToMany(
            mappedBy = "dive",
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    private List<DiveBuddyNameEntity> namedBuddies;

    public DiveEntity() {}

    public DiveEntity(
            final int number,
            final String diveIdentifier,
            final UserEntity userEntity,
            final DiveSiteEntity diveSiteEntity,
            final List<DiveProfileEntity> profiles,
            final List<String> namedBuddies) {
        this.number = number;
        this.diveIdentifier = diveIdentifier;
        this.user = userEntity;
        this.previewImage = null;
        this.diveSite = diveSiteEntity;
        this.profiles =
                profiles.stream()
                        .map(p -> p.setDive(this))
                        .collect(Collectors.toCollection(ArrayList::new));
        this.buddyDivesFrom = new ArrayList<>();
        this.buddyDivesTo = new ArrayList<>();
        this.namedBuddies =
                namedBuddies.stream()
                        .map(b -> new DiveBuddyNameEntity(this, b))
                        .collect(Collectors.toCollection(ArrayList::new));
    }

    private String getPreviewImage(@NotNull final String baseUrl) {
        if (previewImage == null) {
            return null;
        }
        return URI.create(baseUrl).resolve(previewImage).toString();
    }

    public Dive toRecord(final String baseUrl, final boolean includeBuddyDives) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null, check injected services");
        return new Dive(
                id,
                user.toRecord().toFrontendModel(),
                number,
                diveIdentifier,
                getPreviewImage(baseUrl),
                diveSite.toRecord(),
                profiles.stream().map(DiveProfileEntity::toRecord).toList(),
                getBuddyDives(includeBuddyDives)
                        .map(d -> new BuddyDive(d.user.toRecord().toFrontendModel(), d.id))
                        .toList(),
                getNamedBuddiesModels());
    }

    public SimplifiedDive toSimplifiedRecord(
            final String baseUrl, final boolean includeBuddyDives) {
        return new SimplifiedDive(
                id,
                user.toRecord().toFrontendModel(),
                number,
                diveIdentifier,
                getPreviewImage(baseUrl),
                diveSite.toRecord(),
                getBuddyDives(includeBuddyDives).map(DiveEntity::toBuddyDive).toList(),
                getNamedBuddiesModels());
    }

    private Stream<DiveEntity> getBuddyDives(final boolean includeBuddyDives) {
        if (!includeBuddyDives) {
            return Stream.empty();
        }
        return getBuddyDives();
    }

    private Stream<DiveEntity> getBuddyDives() {
        return Stream.concat(buddyDivesFrom.stream(), buddyDivesTo.stream());
    }

    public boolean hasBuddyDive(final long otherId) {
        return getBuddyDives(true).anyMatch(d -> d.id == otherId);
    }

    private BuddyDive toBuddyDive() {
        return new BuddyDive(user.toRecord().toFrontendModel(), id);
    }

    private List<String> getNamedBuddiesModels() {
        return namedBuddies.stream().map(DiveBuddyNameEntity::getName).toList();
    }

    public UserEntity getUserEntity() {
        return user;
    }

    public DiveEntity update(
            final int number,
            final String diveIdentifier,
            @Nullable final DiveSiteEntity diveSiteEntity,
            @Nullable final ArrayList<DiveBuddyNameEntity> namedBuddies) {
        this.number = number;
        this.diveIdentifier = diveIdentifier;
        if (diveSiteEntity != null) {
            this.diveSite = diveSiteEntity;
        }
        if (namedBuddies != null) {
            this.namedBuddies.removeIf(Predicate.not(namedBuddies::contains));
            this.namedBuddies.addAll(CollectionUtils.subtract(namedBuddies, this.namedBuddies));
        }
        return this;
    }

    public List<DiveProfileEntity> getProfiles() {
        return profiles;
    }

    public void addProfiles(final List<DiveProfileEntity> profiles) {
        this.profiles = new ArrayList<>(this.profiles);
        this.profiles.addAll(profiles.stream().map(d -> d.setDive(this)).toList());
    }

    public void setPreviewImage(final String previewImage) {
        this.previewImage = previewImage;
    }

    public long getId() {
        return id;
    }

    public List<DiveBuddyNameEntity> getNamedBuddies() {
        return namedBuddies;
    }

    public void addBuddyDive(final DiveEntity buddyDive) {
        buddyDivesFrom = new ArrayList<>(buddyDivesFrom);
        buddyDivesFrom.add(buddyDive);
        buddyDive.buddyDivesTo = new ArrayList<>(buddyDive.buddyDivesTo);
        buddyDive.buddyDivesTo.add(this);
    }

    public void removeBuddyDive(final DiveEntity buddyDive) {
        if (buddyDivesTo.contains(buddyDive)) {
            buddyDive.removeBuddyDive(this);
            return;
        }
        buddyDivesFrom = new ArrayList<>(buddyDivesFrom);
        buddyDivesFrom.remove(buddyDive);
        buddyDive.buddyDivesTo = new ArrayList<>(buddyDive.buddyDivesTo);
        buddyDive.buddyDivesTo.remove(this);
    }

    public DiveEntity resetProfiles() {
        profiles = new ArrayList<>();
        return this;
    }
}

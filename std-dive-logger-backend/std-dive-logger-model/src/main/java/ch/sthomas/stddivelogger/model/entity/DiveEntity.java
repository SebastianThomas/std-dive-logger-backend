package ch.sthomas.stddivelogger.model.entity;

import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionCalculator;
import ch.sthomas.stddivelogger.model.analytics.CylinderConsumptionResult;
import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.conditions.Visibility;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfiguration;
import ch.sthomas.stddivelogger.model.dive.gear.DiveConfigurationCylinder;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSize;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMode;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.dive.stats.GasConsumptionComparison;
import ch.sthomas.stddivelogger.model.entity.gas.CylinderSizeEntity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.collections4.CollectionUtils;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "t_dives")
@SuppressWarnings("NullAway.Init")
public class DiveEntity {
    private static final Logger logger = LoggerFactory.getLogger(DiveEntity.class);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_dive_id", nullable = false)
    private Long id;

    @Column(name = "dive_number", nullable = false)
    private int number;

    @Column(name = "dive_identifier", nullable = false)
    private String diveIdentifier;

    @Column(name = "preview_image", nullable = false)
    private @Nullable String previewImage;

    @Column(name = "notes", nullable = false)
    private String notes;

    // @OneToOne(mappedBy = ...) has no FK column on this table to join on, so even EAGER here
    // means a *separate* SELECT per dive - N extra round trips for a page of N dives. Tried
    // LAZY + @BatchSize here first, but bidirectional mappedBy one-to-one can't be proxied the
    // normal way (no FK to build a proxy from), and this Hibernate version rejects @BatchSize on
    // an EAGER to-one outright ("Property may not be annotated '@BatchSize'") - fixing this
    // properly needs bytecode enhancement (@LazyToOne(NO_PROXY) or Hibernate's enhance plugin),
    // which is a bigger build-tooling change than this pass. Left EAGER/un-batched for now - see
    // the collections below, which don't have this limitation and got the fix.
    @OneToOne(mappedBy = "dive", cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private @Nullable DiveSummaryEntity diveSummary;

    @OneToOne(mappedBy = "dive", cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private @Nullable VisibilityEntity visibility;

    @OneToOne(mappedBy = "dive", cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private @Nullable DiveConditionsEntity conditions;

    @OneToOne(mappedBy = "dive", cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private @Nullable DiveGasConsumptionEntity gasConsumption;

    @OneToOne(mappedBy = "dive", cascade = CascadeType.ALL)
    @PrimaryKeyJoinColumn
    private @Nullable DiveConfigurationEntity configuration;

    @JoinColumn(name = "fk_diver_id")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private UserEntity user;

    @JoinColumn(name = "dive_site")
    @ManyToOne(cascade = CascadeType.PERSIST)
    private DiveSiteEntity diveSite;

    @OneToMany(mappedBy = "dive", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<DiveProfileEntity> profiles;

    // Read-only from this side - deliberately no cascade/orphanRemoval, since a single
    // DiveBuddyEntity row is reachable from TWO different DiveEntity instances (this one and the
    // other dive's mirrored collection) and dual ownership would make Hibernate's orphan-removal
    // semantics ambiguous. All mutation goes through DiveBuddyRepository directly (see
    // DiveDataService.linkDive/unlinkDive), then the caller's stale in-memory collections here are
    // refreshed via entityManager.clear() + a fresh fetch, same pattern used elsewhere in this
    // class for the named-buddy diffing.
    //
    // LAZY (not EAGER): most dive-list reads pass includeBuddyDives=false and never touch this at
    // all - EAGER would fetch it unconditionally at load time regardless, so LAZY plus the
    // explicit toRecord()-time include flag (see getBuddyLinks(boolean)) means it's only ever
    // queried when actually needed. @BatchSize still collapses it into one IN-query per page on
    // the paths that do need it, instead of one query per dive.
    @OneToMany(mappedBy = "dive")
    @BatchSize(size = 30)
    private List<DiveBuddyEntity> buddyLinksFrom;

    @OneToMany(mappedBy = "buddyDive")
    @BatchSize(size = 30)
    private List<DiveBuddyEntity> buddyLinksTo;

    @OneToMany(
            mappedBy = "dive",
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    @BatchSize(size = 30)
    private List<DiveBuddyNameEntity> namedBuddies;

    @OneToMany(
            mappedBy = "dive",
            cascade = CascadeType.ALL,
            fetch = FetchType.EAGER,
            orphanRemoval = true)
    @BatchSize(size = 30)
    private @Nullable List<DiveTagEntity> tags;

    // One row per (dive, reason) the user has marked "no more info to add" in the backfill guide.
    // Same batched-collection shape as tags/namedBuddies above. See DiveBackfillDismissalEntity for
    // why this is a table rather than a flag on this entity.
    @OneToMany(mappedBy = "dive", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    private List<DiveBackfillDismissalEntity> backfillDismissals;

    @Column(name = "fk_leader_named_buddy_id")
    private @Nullable Long leaderNamedBuddyId;

    @Column(name = "fk_leader_buddy_dive_id")
    private @Nullable Long leaderBuddyDiveId;

    /** True only when the owner explicitly picked "Me" as leader - see {@link DiveLeader}. */
    @Column(name = "leader_self_explicit")
    private boolean leaderSelfExplicit;

    @Column(name = "team_terminology")
    @Enumerated(EnumType.STRING)
    private @Nullable TeamTerminology teamTerminology;

    /**
     * Diver-set "star" - surfaces the dive on the home dashboard and in the list's highlight
     * filter.
     */
    @Column(name = "highlighted", nullable = false)
    private boolean highlighted;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public DiveEntity() {}

    public DiveEntity(
            final int number,
            final String diveIdentifier,
            final String notes,
            final Visibility visibility,
            final DiveGasConsumption gasConsumption,
            final SuitEntity suit,
            @Nullable final CcrUnitEntity ccrUnit,
            @Nullable final CcrUnitEntity secondaryCcrUnit,
            final DiveConfiguration configuration,
            final UserEntity userEntity,
            final DiveSiteEntity diveSiteEntity,
            final List<DiveProfileEntity> profiles,
            final List<String> namedBuddies,
            final Function<CylinderSize, CylinderSizeEntity> getCylinderSizeEntity) {
        this.number = number;
        this.diveIdentifier = diveIdentifier;
        this.visibility = new VisibilityEntity(this, visibility);
        this.gasConsumption = new DiveGasConsumptionEntity(this, gasConsumption);
        this.configuration =
                new DiveConfigurationEntity(
                        this,
                        suit,
                        ccrUnit,
                        secondaryCcrUnit,
                        configuration,
                        getCylinderSizeEntity);
        this.user = userEntity;
        this.previewImage = null;
        this.notes = notes;
        this.diveSite = diveSiteEntity;
        this.profiles =
                profiles.stream()
                        .map(p -> p.setDive(this))
                        .collect(Collectors.toCollection(ArrayList::new));
        this.buddyLinksFrom = new ArrayList<>();
        this.buddyLinksTo = new ArrayList<>();
        this.namedBuddies =
                namedBuddies.stream()
                        .map(b -> new DiveBuddyNameEntity(this, b))
                        .collect(Collectors.toCollection(ArrayList::new));
        this.diveSummary = new DiveSummaryEntity(this);
        this.tags = new ArrayList<>();
        this.backfillDismissals = new ArrayList<>();
    }

    private @Nullable String getPreviewImage(@NotNull final String baseUrl) {
        if (previewImage == null) {
            return null;
        }
        return URI.create(baseUrl).resolve(previewImage).toString();
    }

    public Dive toRecord(final String baseUrl, final boolean includeBuddyDives) {
        Objects.requireNonNull(baseUrl, "baseUrl must not be null, check injected services");
        final var profileRecords = profiles.stream().map(DiveProfileEntity::toRecord).toList();
        final var cylinders =
                Optional.ofNullable(configuration)
                        .map(DiveConfigurationEntity::toRecord)
                        .map(DiveConfiguration::cylinders)
                        .orElse(List.of());
        return new Dive(
                id,
                user.toRecord().toFrontendModel(),
                number,
                notes,
                diveIdentifier,
                getPreviewImage(baseUrl),
                Optional.ofNullable(visibility).map(VisibilityEntity::toRecord).orElse(null),
                Optional.ofNullable(gasConsumption)
                        .map(DiveGasConsumptionEntity::toRecord)
                        .orElse(null),
                cylinders.isEmpty()
                        ? null
                        : CylinderConsumptionCalculator.calculate(profileRecords, cylinders),
                gasConsumptionComparison(),
                Optional.ofNullable(configuration)
                        .map(DiveConfigurationEntity::toRecord)
                        .orElse(null),
                diveSite.toRecord(),
                profileRecords,
                getBuddyLinks(includeBuddyDives).map(l -> toBuddyDive(l, this.id)).toList(),
                getNamedBuddiesRecords(),
                getSummary(),
                getTags(),
                Optional.ofNullable(conditions)
                        .map(DiveConditionsEntity::getWaterType)
                        .orElse(null),
                Optional.ofNullable(conditions)
                        .map(DiveConditionsEntity::toCurrentRecord)
                        .orElse(null),
                getLeader(),
                teamTerminology,
                highlighted);
    }

    /**
     * Inserted-vs-calculated gas-consumption reconciliation for this dive, or {@code null} when
     * there's nothing to compare. Kept cheap for {@link #toBackfillStatus} (run per-dive over the
     * whole backfill queue): bails before building any profile records unless {@code
     * gasConsumption} is a real, non-{@link DiveGasConsumption#EMPTY} value, and only runs {@link
     * CylinderConsumptionCalculator} when OC cylinders with both start/end bar exist. Suppressed
     * entirely on a CCR dive - a whole-dive RMV/total isn't a meaningful concept for a closed loop.
     */
    private @Nullable GasConsumptionComparison gasConsumptionComparison() {
        final var gas =
                Optional.ofNullable(gasConsumption)
                        .map(DiveGasConsumptionEntity::toRecord)
                        .orElse(null);
        if (gas == null || gas.equals(DiveGasConsumption.EMPTY)) {
            return null;
        }
        final var summary = getSummary();
        final List<DiveConfigurationCylinder> cylinders =
                Optional.ofNullable(configuration)
                        .map(DiveConfigurationEntity::toRecord)
                        .map(DiveConfiguration::cylinders)
                        .orElse(List.of());
        // Any pressure-usable cylinder (not just OC) - a CCR configuration on an OC/gauge profile
        // still wants the per-cylinder breakdown (bailout RMV, O2/diluent litres).
        final var hasUsableCylinder =
                cylinders.stream().anyMatch(c -> c.startBar() != null && c.endBar() != null);
        CylinderConsumptionResult cylinderResult = CylinderConsumptionResult.EMPTY;
        if (hasUsableCylinder) {
            final var profileRecords = profiles.stream().map(DiveProfileEntity::toRecord).toList();
            final var isCcr =
                    profileRecords.stream()
                            .map(DiveProfile::measurements)
                            .filter(Objects::nonNull)
                            .flatMap(List::stream)
                            .anyMatch(m -> m.measurement().mode() == DiveMode.CC);
            if (isCcr) {
                // A whole-dive RMV/total isn't a meaningful concept for a closed loop.
                return null;
            }
            cylinderResult = CylinderConsumptionCalculator.calculate(profileRecords, cylinders);
        }
        final var comparison =
                GasConsumptionComparison.of(
                        gas,
                        cylinderResult,
                        summary.averageDepth(),
                        summary.bottomTime().toSeconds());
        // Nothing calculable or derivable to compare against - not a mismatch, just absent.
        if (comparison.calculatedRmvLiters() == null
                && comparison.calculatedTotalLiters() == null
                && comparison.impliedRmvFromTotalLiters() == null) {
            return null;
        }
        return comparison;
    }

    private DiveLeader getLeader() {
        if (leaderNamedBuddyId != null) {
            return new DiveLeader(DiveLeader.LeaderType.NAMED, leaderNamedBuddyId, null);
        }
        if (leaderBuddyDiveId != null) {
            return new DiveLeader(DiveLeader.LeaderType.LINKED, null, leaderBuddyDiveId);
        }
        return leaderSelfExplicit ? DiveLeader.SELF : DiveLeader.UNSET;
    }

    public SimplifiedDive toSimplifiedRecord(
            final String baseUrl, final boolean includeBuddyDives) {
        return new SimplifiedDive(
                id,
                user.toRecord().toFrontendModel(),
                number,
                diveIdentifier,
                getPreviewImage(baseUrl),
                Optional.ofNullable(visibility).map(VisibilityEntity::toRecord).orElse(null),
                diveSite.toRecord(),
                getBuddyLinks(includeBuddyDives).map(l -> toBuddyDive(l, this.id)).toList(),
                getNamedBuddiesModels(),
                getSummary(),
                getTags(),
                highlighted);
    }

    /**
     * The backfill checklist for this dive - see {@link DiveBackfillStatus}'s own doc for what each
     * key means. Deliberately reads the raw entity graph rather than going through {@link
     * #toRecord}, since that also computes cylinder consumption/buddy links/tags this check doesn't
     * need.
     */
    public DiveBackfillStatus toBackfillStatus() {
        final var missing = new ArrayList<DiveBackfillField>();
        final var visibilityRecord =
                Optional.ofNullable(visibility).map(VisibilityEntity::toRecord).orElse(null);
        if (visibilityRecord == null
                || (visibilityRecord.meters() == null
                        && (visibilityRecord.description() == null
                                || visibilityRecord.description().isBlank())
                        && visibilityRecord.feeling() == null)) {
            missing.add(DiveBackfillField.VISIBILITY);
        }
        final var gasRecord =
                Optional.ofNullable(gasConsumption)
                        .map(DiveGasConsumptionEntity::toRecord)
                        .orElse(null);
        // Not a gap when the tracked cylinders carry pressures - the calculator derives RMV / total
        // litres from those, so a manual whole-dive entry isn't required.
        final var hasCylinderGasData =
                Optional.ofNullable(configuration)
                        .map(DiveConfigurationEntity::toRecord)
                        .map(DiveConfiguration::cylinders)
                        .orElse(List.of())
                        .stream()
                        .anyMatch(c -> c.startBar() != null && c.endBar() != null);
        if ((gasRecord == null || gasRecord.equals(DiveGasConsumption.EMPTY))
                && !hasCylinderGasData) {
            missing.add(DiveBackfillField.GAS_CONSUMPTION);
        }
        final var gasComparison = gasConsumptionComparison();
        if (gasComparison != null && gasComparison.mismatch()) {
            missing.add(DiveBackfillField.GAS_CONSUMPTION_MISMATCH);
        }
        if (getLeader().type() == DiveLeader.LeaderType.UNSET) {
            missing.add(DiveBackfillField.LEADER);
        }
        if (notes == null || notes.isBlank()) {
            missing.add(DiveBackfillField.NOTES);
        }
        final var dismissed = new ArrayList<>(getDismissedBackfillFields());
        return new DiveBackfillStatus(
                id,
                number,
                diveIdentifier,
                getSummary().start(),
                diveSite.getId(),
                diveSite.getName(),
                missing,
                dismissed);
    }

    /** Reasons the user has explicitly marked "no more info to add" for this dive. */
    public Set<DiveBackfillField> getDismissedBackfillFields() {
        return backfillDismissals.stream()
                .map(DiveBackfillDismissalEntity::getReason)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DiveBackfillField.class)));
    }

    /** Marks one backfill reason "no more info to add" for this dive (no-op if already set). */
    public void dismissBackfillField(final DiveBackfillField reason) {
        if (backfillDismissals.stream().noneMatch(d -> d.getReason() == reason)) {
            backfillDismissals.add(new DiveBackfillDismissalEntity(this, reason));
        }
    }

    /** Clears a "no more info" dismissal, moving the reason back into the active queue. */
    public void restoreBackfillField(final DiveBackfillField reason) {
        backfillDismissals.removeIf(d -> d.getReason() == reason);
    }

    private DiveSummary getSummary() {
        return Optional.ofNullable(diveSummary)
                .orElseGet(() -> new DiveSummaryEntity(this))
                .toRecord();
    }

    private Stream<DiveBuddyEntity> getBuddyLinks(final boolean includeBuddyDives) {
        if (!includeBuddyDives) {
            return Stream.empty();
        }
        return getBuddyLinks();
    }

    private Stream<DiveBuddyEntity> getBuddyLinks() {
        return Stream.concat(buddyLinksFrom.stream(), buddyLinksTo.stream());
    }

    /**
     * The other dive on the far side of a link row from this dive's point of view. May be a lazy
     * Hibernate proxy - callers must only touch it via its public getters (never a private method
     * or direct field access), since a proxy only intercepts non-private methods.
     */
    private DiveEntity otherSideOf(final DiveBuddyEntity link) {
        // Long == Long is identity comparison - relies on this.id and link.getDive().getId()
        // being the exact same boxed instance, which today only holds because of the persistence
        // context's identity guarantee. Use .equals() so this stays correct even if that
        // assumption ever breaks (detached/merged entities, 2nd-level cache, bytecode enhancement).
        return link.getDive().getId().equals(this.id) ? link.getBuddyDive() : link.getDive();
    }

    private BuddyDive toBuddyDive(final DiveBuddyEntity link, final long viewpointDiveId) {
        final var other = otherSideOf(link);
        return new BuddyDive(
                other.getUserEntity().toRecord().toFrontendModel(),
                other.getId(),
                link.roleAsSeenFrom(viewpointDiveId));
    }

    public boolean hasBuddyDive(final long otherId) {
        return getBuddyLinks(true).anyMatch(l -> otherSideOf(l).getId() == otherId);
    }

    /**
     * The role of {@code otherId}'s diver, as seen from this dive - null if not actually linked.
     */
    public @Nullable BuddyRole getBuddyDiveRole(final long otherId) {
        return getBuddyLinks(true)
                .filter(l -> otherSideOf(l).getId() == otherId)
                .findFirst()
                .map(l -> l.roleAsSeenFrom(this.id))
                .orElse(null);
    }

    private List<String> getNamedBuddiesModels() {
        return namedBuddies.stream().map(DiveBuddyNameEntity::getName).toList();
    }

    private List<NamedBuddy> getNamedBuddiesRecords() {
        return namedBuddies.stream().map(DiveBuddyNameEntity::toRecord).toList();
    }

    public UserEntity getUserEntity() {
        return user;
    }

    public DiveEntity update(
            final int number,
            @Nullable final String diveIdentifier,
            @Nullable final String notes,
            @Nullable final DiveSiteEntity diveSiteEntity,
            @Nullable final ArrayList<DiveBuddyNameEntity> namedBuddies,
            @Nullable final DiveConfigurationEntity configuration,
            @Nullable final DiveGasConsumptionEntity gasConsumption,
            @Nullable final VisibilityEntity visibility,
            // Applied unconditionally (unlike the @Nullable params above, which mean "leave
            // unchanged" when null) - the caller's current leader selection is always written
            // through, including "no explicit choice" (both ids null and leaderSelfExplicit
            // false), which is a legitimate target state distinct from "I led" (both ids null and
            // leaderSelfExplicit true). See DiveLeader's own doc comment.
            @Nullable final Long leaderNamedBuddyId,
            @Nullable final Long leaderBuddyDiveId,
            final boolean leaderSelfExplicit,
            @Nullable final TeamTerminology teamTerminology) {
        this.number = number;
        if (diveIdentifier != null) {
            this.diveIdentifier = diveIdentifier;
        }
        if (notes != null) {
            this.notes = notes;
        }
        if (diveSiteEntity != null) {
            this.diveSite = diveSiteEntity;
        }
        if (namedBuddies != null) {
            this.namedBuddies.removeIf(Predicate.not(namedBuddies::contains));
            this.namedBuddies.addAll(CollectionUtils.subtract(namedBuddies, this.namedBuddies));
        }
        if (configuration != null) {
            this.configuration = configuration;
        }
        if (gasConsumption != null) {
            this.gasConsumption = gasConsumption;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
        this.leaderNamedBuddyId = leaderNamedBuddyId;
        this.leaderBuddyDiveId = leaderBuddyDiveId;
        // Normalized defensively so an inconsistent caller can never persist "self-explicit" and a
        // named/linked leader at the same time (UpdateDiveBody already rejects this combination,
        // but this is the one place that actually writes the column).
        this.leaderSelfExplicit =
                leaderSelfExplicit && leaderNamedBuddyId == null && leaderBuddyDiveId == null;
        this.teamTerminology = teamTerminology;
        this.updateDiveSummary();
        return this;
    }

    /**
     * Applies only the specific fields a reimport-in-place conflict was resolved to overwrite -
     * deliberately narrower than {@link #update}, which also unconditionally rewrites the
     * leader/team-terminology columns even when its own leader params are null. Reimport must never
     * touch those (or number, identifier, site, configuration) at all - only notes/visibility/
     * namedBuddies/gasConsumption, and only when the caller explicitly resolved a conflict on that
     * field (null here means "leave it exactly as the user already had it").
     */
    public void applyReimportResolution(
            @Nullable final String notes,
            @Nullable final Visibility visibility,
            @Nullable final List<String> namedBuddies,
            @Nullable final DiveGasConsumption gasConsumption) {
        if (notes != null) {
            this.notes = notes;
        }
        if (visibility != null) {
            this.visibility = new VisibilityEntity(this, visibility);
        }
        if (namedBuddies != null) {
            final var resolved =
                    namedBuddies.stream()
                            .map(b -> new DiveBuddyNameEntity(this, b))
                            .collect(Collectors.toCollection(ArrayList::new));
            this.namedBuddies.removeIf(Predicate.not(resolved::contains));
            this.namedBuddies.addAll(CollectionUtils.subtract(resolved, this.namedBuddies));
        }
        if (gasConsumption != null) {
            this.gasConsumption = new DiveGasConsumptionEntity(this, gasConsumption);
        }
        this.updateDiveSummary();
    }

    public DiveEntity updateDiveSummary() {
        if (diveSummary != null) {
            diveSummary.update(this);
        } else {
            this.diveSummary = new DiveSummaryEntity(this);
        }
        return this;
    }

    /**
     * Lets a diver explicitly set a manual dive's average depth (see
     * DiveSummaryEntity#setAverageDepth for why it can't be computed for one). Call before {@link
     * #update} / {@link #updateDiveSummary} so the explicit value isn't left stale.
     */
    public void setAverageDepth(final @Nullable Double averageDepth) {
        if (diveSummary == null) {
            this.diveSummary = new DiveSummaryEntity(this);
        }
        diveSummary.setAverageDepth(averageDepth);
    }

    /**
     * Recomputes auto-detected tags from the given candidates. Should be called by the service
     * layer after any structural change to the dive (profiles, configuration).
     */
    public void recomputeAutoTags(final Collection<TagDefinitionEntity> autoDetectCandidates) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        // Remove non-dismissed auto tags — dismissed rows stay so they are not re-added.
        tags.removeIf(t -> !t.isManual() && !t.isDismissed());
        // Collect IDs already covered by a manual or dismissed tag so we don't insert duplicates.
        final var coveredTagIds =
                tags.stream()
                        .map(t -> t.getTag().getId())
                        .collect(java.util.stream.Collectors.toSet());
        autoDetectCandidates.stream()
                .filter(def -> matchesAutoDetect(def.getAutoDetectRule()))
                .filter(def -> !coveredTagIds.contains(def.getId()))
                .map(def -> new DiveTagEntity(this, def, false))
                .forEach(tags::add);
    }

    public boolean matchesAutoDetect(@Nullable final AutoDetectRule rule) {
        if (rule == null) {
            return false;
        }
        return switch (rule) {
            case CCR ->
                    configuration != null
                            && (configuration.getCcrUnitEntity() != null
                                    || configuration.getSecondaryCcrUnitEntity() != null);
            case DECO -> hasDeco();
        };
    }

    // Each DecoStop.seconds() is a remaining-time-at-this-point reading (FIT's next_stop_time,
    // Suunto JSON's TimeToSurface) sampled repeatedly through a stop - summing it inflates the
    // real obligation many times over. The peak reading across the dive is the actual severity;
    // 5min was picked against two real dives (~8.9min real deco vs. ~3.1min NDL-scratch/no deco).
    private static final long MINIMUM_DECO_MINUTES = 5;

    private boolean hasDeco() {
        if (profiles == null) {
            return false;
        }
        final var maxDecoSeconds =
                profiles.stream()
                        .flatMap(DiveProfileEntity::getMeasurementsStream)
                        .filter(m -> m.getDecoStops() != null)
                        .flatMap(m -> m.getDecoStops().stream())
                        .mapToLong(d -> d.seconds())
                        .max()
                        .orElse(0);
        return Duration.ofSeconds(maxDecoSeconds).toMinutes() >= MINIMUM_DECO_MINUTES;
    }

    public void setManualTags(final Collection<TagDefinitionEntity> manualTagDefs) {
        if (tags == null) {
            tags = new ArrayList<>();
        }
        final var manualDefIds =
                manualTagDefs.stream()
                        .map(TagDefinitionEntity::getId)
                        .collect(java.util.stream.Collectors.toSet());
        // Remove all existing manual tags AND any auto/dismissed tags whose tag ID
        // appears in the incoming manual set (re-adding manually clears dismissed flag).
        tags.removeIf(t -> t.isManual() || manualDefIds.contains(t.getTag().getId()));
        manualTagDefs.stream()
                .map(def -> new DiveTagEntity(this, def, true, false))
                .forEach(tags::add);
    }

    public List<TagDefinition> getTags() {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(t -> !t.isDismissed())
                .map(t -> t.getTag().toRecord())
                .distinct()
                .toList();
    }

    public List<DiveProfileEntity> getProfiles() {
        return profiles;
    }

    public void addProfiles(final List<DiveProfileEntity> profiles) {
        this.profiles = new ArrayList<>(this.profiles);
        this.profiles.addAll(profiles.stream().map(d -> d.setDive(this)).toList());
        this.updateDiveSummary();
    }

    public void setPreviewImage(final String previewImage) {
        this.previewImage = previewImage;
    }

    public Long getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getDiveIdentifier() {
        return diveIdentifier;
    }

    public @Nullable TeamTerminology getTeamTerminology() {
        return teamTerminology;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(final boolean highlighted) {
        this.highlighted = highlighted;
    }

    public List<DiveBuddyNameEntity> getNamedBuddies() {
        return namedBuddies;
    }

    public void appendNotes(final String newNotes) {
        this.notes += "\n\n" + newNotes;
    }

    public long getUserId() {
        return user.getId();
    }

    public @Nullable DiveConfigurationEntity getConfiguration() {
        return configuration;
    }

    public void setConfiguration(final DiveConfigurationEntity configuration) {
        this.configuration = configuration;
    }

    public @Nullable DiveGasConsumptionEntity getGasConsumption() {
        return gasConsumption;
    }

    public void setGasConsumption(final DiveGasConsumptionEntity gasConsumption) {
        this.gasConsumption = gasConsumption;
    }

    public @Nullable VisibilityEntity getVisibility() {
        return visibility;
    }

    public void setVisibility(final VisibilityEntity visibility) {
        this.visibility = visibility;
    }

    public @Nullable DiveConditionsEntity getConditions() {
        return conditions;
    }

    public void setConditions(final DiveConditionsEntity conditions) {
        this.conditions = conditions;
    }
}

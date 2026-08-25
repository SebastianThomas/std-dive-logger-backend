package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.model.PagedResponse;
import ch.sthomas.stddivelogger.data.repository.DiveBuddyNameRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveTripDefaultTeamRepository;
import ch.sthomas.stddivelogger.data.repository.DiveTripMemberRepository;
import ch.sthomas.stddivelogger.data.repository.DiveTripRepository;
import ch.sthomas.stddivelogger.data.repository.UserRepository;
import ch.sthomas.stddivelogger.model.dive.BasicDiveInfo;
import ch.sthomas.stddivelogger.model.dive.BuddyRole;
import ch.sthomas.stddivelogger.model.dive.TeamTerminology;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTrip;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripDefaultTeamMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripListEntry;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripMember;
import ch.sthomas.stddivelogger.model.dive.trip.DiveTripType;
import ch.sthomas.stddivelogger.model.entity.DiveBuddyNameEntity;
import ch.sthomas.stddivelogger.model.entity.DiveTripDefaultTeamEntity;
import ch.sthomas.stddivelogger.model.entity.DiveTripEntity;
import ch.sthomas.stddivelogger.model.entity.DiveTripMemberEntity;

import jakarta.persistence.EntityManager;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A trip's members form a DAG (dive-trips-containing-dive-trips, for nesting - see {@code
 * t_dive_trip_member}'s own doc). Cycle prevention and transitive listing both use a bounded
 * recursive CTE (depth-capped at {@link #MAX_TRIP_DEPTH}) rather than walking the tree in Java -
 * cheaper, and correct regardless of how deep an actual hierarchy ever gets in practice.
 */
@Service
public class DiveTripDataService {

    private static final int MAX_TRIP_DEPTH = 10;

    private final DiveTripRepository diveTripRepository;
    private final DiveTripMemberRepository diveTripMemberRepository;
    private final DiveTripDefaultTeamRepository diveTripDefaultTeamRepository;
    private final DiveRepository diveRepository;
    private final UserRepository userRepository;
    private final DiveBuddyNameRepository diveBuddyNameRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final EntityManager entityManager;

    public DiveTripDataService(
            final DiveTripRepository diveTripRepository,
            final DiveTripMemberRepository diveTripMemberRepository,
            final DiveTripDefaultTeamRepository diveTripDefaultTeamRepository,
            final DiveRepository diveRepository,
            final UserRepository userRepository,
            final DiveBuddyNameRepository diveBuddyNameRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            final EntityManager entityManager) {
        this.diveTripRepository = diveTripRepository;
        this.diveTripMemberRepository = diveTripMemberRepository;
        this.diveTripDefaultTeamRepository = diveTripDefaultTeamRepository;
        this.diveRepository = diveRepository;
        this.entityManager = entityManager;
        this.userRepository = userRepository;
        this.diveBuddyNameRepository = diveBuddyNameRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /**
     * Every one of the owner's trips, each paired with the earliest/latest start date across every
     * dive transitively under it (its own direct dives and every nested sub-trip's) - both null for
     * a trip with no dives yet. Ordered by that latest date, most recent first, with a dateless
     * (empty) trip sorted to the very top - it's presumably one actively being built right now, not
     * one to bury under dated ones. Never orders by id/creation order: a trip logged after the fact
     * for dives from months ago shouldn't jump to the top just because it was *entered* recently.
     *
     * <p>One query computes every trip's range at once (the recursive CTE's anchor is every owned
     * trip simultaneously, tagged with its own root so the recursion never mixes them up) rather
     * than one query per trip - cheap regardless of how many trips the user has.
     */
    @Transactional(readOnly = true)
    public List<DiveTripListEntry> findTripsByOwnerWithDateRange(final long ownerId) {
        final var trips = diveTripRepository.findByOwner_IdOrderByCreatedAtDesc(ownerId);
        if (trips.isEmpty()) {
            return List.of();
        }
        final var sql =
                """
                WITH RECURSIVE trip_tree AS (
                    SELECT pk_trip_id AS root_trip_id, pk_trip_id AS trip_id, 0 AS depth
                    FROM t_dive_trip
                    WHERE fk_owner_user_id = :ownerId
                    UNION ALL
                    SELECT tt.root_trip_id, m.fk_member_trip_id, tt.depth + 1
                    FROM t_dive_trip_member m
                    JOIN trip_tree tt ON m.fk_trip_id = tt.trip_id
                    WHERE m.fk_member_trip_id IS NOT NULL AND tt.depth < :maxDepth
                )
                SELECT tt.root_trip_id AS trip_id,
                       MIN(ds.dive_start) AS first_dive,
                       MAX(ds.dive_start) AS last_dive
                FROM trip_tree tt
                JOIN t_dive_trip_member m
                    ON m.fk_trip_id = tt.trip_id AND m.fk_member_dive_id IS NOT NULL
                JOIN t_dive_summary ds ON ds.fk_dive_id = m.fk_member_dive_id
                GROUP BY tt.root_trip_id
                """;
        final var params =
                new MapSqlParameterSource()
                        .addValue("ownerId", ownerId)
                        .addValue("maxDepth", MAX_TRIP_DEPTH);
        final var dateRangesByTripId =
                namedParameterJdbcTemplate
                        .query(
                                sql,
                                params,
                                (rs, rowNum) ->
                                        Map.entry(
                                                rs.getLong("trip_id"),
                                                Map.entry(
                                                        rs.getTimestamp("first_dive").toInstant(),
                                                        rs.getTimestamp("last_dive").toInstant())))
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return trips.stream()
                .map(
                        entity -> {
                            final var range = dateRangesByTripId.get(entity.getId());
                            return new DiveTripListEntry(
                                    entity.toRecord(),
                                    range == null ? null : range.getKey(),
                                    range == null ? null : range.getValue());
                        })
                .sorted(
                        Comparator.comparing(
                                DiveTripListEntry::lastDiveDate,
                                Comparator.nullsFirst(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<DiveTrip> findTripById(final long id) {
        return diveTripRepository.findById(id).map(DiveTripEntity::toRecord);
    }

    private DiveTripEntity getTripEntity(final long id) {
        return diveTripRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Could not find dive trip " + id));
    }

    @Transactional
    public DiveTrip createTrip(final long ownerId, final String name, final DiveTripType type) {
        final var owner =
                userRepository
                        .findById(ownerId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Could not find user " + ownerId));
        return diveTripRepository.save(new DiveTripEntity(name, type, owner)).toRecord();
    }

    @Transactional
    public DiveTrip updateTrip(
            final long tripId,
            final String name,
            final DiveTripType type,
            @Nullable final TeamTerminology teamTerminology) {
        final var entity = getTripEntity(tripId);
        entity.setName(name);
        entity.setType(type);
        entity.setTeamTerminology(teamTerminology);
        return diveTripRepository.save(entity).toRecord();
    }

    @Transactional
    public void deleteTrip(final long tripId) {
        diveTripRepository.deleteById(tripId);
    }

    @Transactional(readOnly = true)
    public List<DiveTripMember> findDirectMembers(final long tripId) {
        return diveTripMemberRepository.findByTrip_Id(tripId).stream()
                .map(
                        m -> {
                            final var dive = m.getMemberDive();
                            if (dive != null) {
                                return new DiveTripMember(
                                        DiveTripMember.MemberType.DIVE,
                                        new BasicDiveInfo(
                                                dive.getId(),
                                                dive.getNumber(),
                                                dive.getDiveIdentifier()),
                                        null);
                            }
                            final var subTrip = Objects.requireNonNull(m.getMemberTrip());
                            return new DiveTripMember(
                                    DiveTripMember.MemberType.TRIP, null, subTrip.toRecord());
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiveTrip> findTripsContainingDive(final long diveId) {
        return diveTripMemberRepository.findByMemberDive_Id(diveId).stream()
                .map(m -> m.getTrip().toRecord())
                .toList();
    }

    /**
     * True if {@code target} is already reachable, directly or transitively, as a member of {@code
     * start} - i.e. adding {@code start} as a member of {@code target} would close a cycle.
     */
    @Transactional(readOnly = true)
    public boolean wouldCreateCycle(final long start, final long target) {
        if (start == target) {
            return true;
        }
        final var sql =
                """
                WITH RECURSIVE descendants AS (
                    SELECT fk_member_trip_id AS trip_id, 1 AS depth
                    FROM t_dive_trip_member
                    WHERE fk_trip_id = :startId AND fk_member_trip_id IS NOT NULL
                    UNION ALL
                    SELECT m.fk_member_trip_id, d.depth + 1
                    FROM t_dive_trip_member m
                    JOIN descendants d ON m.fk_trip_id = d.trip_id
                    WHERE m.fk_member_trip_id IS NOT NULL AND d.depth < :maxDepth
                )
                SELECT EXISTS(SELECT 1 FROM descendants WHERE trip_id = :targetId)
                """;
        final var params =
                new MapSqlParameterSource()
                        .addValue("startId", start)
                        .addValue("targetId", target)
                        .addValue("maxDepth", MAX_TRIP_DEPTH);
        return Boolean.TRUE.equals(
                namedParameterJdbcTemplate.queryForObject(sql, params, Boolean.class));
    }

    @Transactional
    public void addDiveMember(final long tripId, final long diveId) {
        final var trip = getTripEntity(tripId);
        final var dive =
                diveRepository
                        .findById(diveId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Could not find dive " + diveId));
        if (diveTripMemberRepository.existsByTrip_IdAndMemberDive_Id(tripId, diveId)) {
            return;
        }
        diveTripMemberRepository.save(DiveTripMemberEntity.forDive(trip, dive));
    }

    @Transactional
    public void removeDiveMember(final long tripId, final long diveId) {
        diveTripMemberRepository
                .findByTrip_IdAndMemberDive_Id(tripId, diveId)
                .ifPresent(diveTripMemberRepository::delete);
    }

    @Transactional
    public void addTripMember(final long tripId, final long memberTripId) {
        if (wouldCreateCycle(memberTripId, tripId)) {
            throw new IllegalArgumentException(
                    "Adding trip "
                            + memberTripId
                            + " to trip "
                            + tripId
                            + " would create a cycle.");
        }
        final var trip = getTripEntity(tripId);
        final var memberTrip = getTripEntity(memberTripId);
        if (diveTripMemberRepository.existsByTrip_IdAndMemberTrip_Id(tripId, memberTripId)) {
            return;
        }
        diveTripMemberRepository.save(DiveTripMemberEntity.forTrip(trip, memberTrip));
    }

    @Transactional
    public void removeTripMember(final long tripId, final long memberTripId) {
        diveTripMemberRepository
                .findByTrip_IdAndMemberTrip_Id(tripId, memberTripId)
                .ifPresent(diveTripMemberRepository::delete);
    }

    /** Every dive transitively under this trip - direct dive members plus every sub-trip's. */
    @Transactional(readOnly = true)
    public PagedResponse<BasicDiveInfo> findTransitiveDives(
            final long tripId, final int page, final int pageSize) {
        final var sql =
                """
                WITH RECURSIVE trip_tree AS (
                    SELECT CAST(:tripId AS INTEGER) AS trip_id, 0 AS depth
                    UNION ALL
                    SELECT m.fk_member_trip_id, t.depth + 1
                    FROM t_dive_trip_member m
                    JOIN trip_tree t ON m.fk_trip_id = t.trip_id
                    WHERE m.fk_member_trip_id IS NOT NULL AND t.depth < :maxDepth
                )
                SELECT DISTINCT d.pk_dive_id AS dive_id, d.dive_number AS dive_number,
                       d.dive_identifier AS dive_identifier
                FROM t_dive_trip_member m
                JOIN trip_tree t ON m.fk_trip_id = t.trip_id
                JOIN t_dives d ON d.pk_dive_id = m.fk_member_dive_id
                WHERE m.fk_member_dive_id IS NOT NULL
                ORDER BY dive_number
                """;
        final var params =
                new MapSqlParameterSource()
                        .addValue("tripId", tripId)
                        .addValue("maxDepth", MAX_TRIP_DEPTH);
        final var all =
                namedParameterJdbcTemplate.query(
                        sql,
                        params,
                        (rs, rowNum) ->
                                new BasicDiveInfo(
                                        rs.getLong("dive_id"),
                                        rs.getLong("dive_number"),
                                        rs.getString("dive_identifier")));
        final var from = (int) Math.min((long) page * pageSize, all.size());
        final var to = (int) Math.min((long) from + pageSize, all.size());
        final var totalPages = (int) Math.ceil(all.size() / (double) pageSize);
        return new PagedResponse<>(pageSize, totalPages, all.size(), all.subList(from, to));
    }

    @Transactional(readOnly = true)
    public List<DiveTripDefaultTeamMember> findDefaultTeam(final long tripId) {
        return diveTripDefaultTeamRepository.findByTrip_Id(tripId).stream()
                .map(DiveTripDefaultTeamEntity::toRecord)
                .toList();
    }

    public record DefaultTeamEntry(
            @Nullable Long buddyUserId, @Nullable String buddyName, BuddyRole role) {}

    @Transactional
    public List<DiveTripDefaultTeamMember> replaceDefaultTeam(
            final long tripId, final List<DefaultTeamEntry> entries) {
        final var trip = getTripEntity(tripId);
        diveTripDefaultTeamRepository.deleteByTrip_Id(tripId);
        diveTripDefaultTeamRepository.flush();
        final var saved =
                entries.stream()
                        .map(
                                e ->
                                        diveTripDefaultTeamRepository.save(
                                                new DiveTripDefaultTeamEntity(
                                                        trip,
                                                        e.buddyUserId() != null
                                                                ? userRepository
                                                                        .findById(e.buddyUserId())
                                                                        .orElseThrow()
                                                                : null,
                                                        e.buddyName(),
                                                        e.role())))
                        .map(DiveTripDefaultTeamEntity::toRecord)
                        .toList();
        return saved;
    }

    /**
     * Prefill-only: copies the trip's default team onto the dive's named buddies, but only when the
     * dive doesn't already have any (never overwrites a dive that's already been edited).
     */
    @Transactional
    public void seedDiveBuddiesFromDefaultTeam(final long tripId, final long diveId) {
        final var dive =
                diveRepository
                        .findById(diveId)
                        .orElseThrow(
                                () -> new NoSuchElementException("Could not find dive " + diveId));
        if (!dive.getNamedBuddies().isEmpty()) {
            return;
        }
        final var team = diveTripDefaultTeamRepository.findByTrip_Id(tripId);
        final var entities =
                team.stream()
                        .map(DiveTripDefaultTeamEntity::toRecord)
                        .map(
                                record -> {
                                    final var name =
                                            record.buddyUser() != null
                                                    ? record.buddyUser().name()
                                                    : Objects.requireNonNull(record.buddyName());
                                    return new DiveBuddyNameEntity(dive, name, record.role());
                                })
                        .toList();
        if (!entities.isEmpty()) {
            diveBuddyNameRepository.saveAll(entities);
            // Same-transaction re-reads of this dive would otherwise see `dive`'s stale in-memory
            // namedBuddies (it was never Hibernate-hydrated - see DiveEntity's constructor, which
            // sets it as a plain immutable list, not a lazy PersistentCollection) rather than the
            // rows just inserted above.
            entityManager.flush();
            entityManager.clear();
        }
    }
}

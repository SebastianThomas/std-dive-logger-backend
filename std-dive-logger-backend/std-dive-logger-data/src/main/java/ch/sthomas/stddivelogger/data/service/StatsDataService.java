package ch.sthomas.stddivelogger.data.service;

import static ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature.TemperatureUnit.CELSIUS;

import ch.sthomas.stddivelogger.data.repository.DiveMeasurementRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsDataService {

    @PersistenceContext private EntityManager entityManager;

    private final DiveRepository diveRepository;
    private final DiveMeasurementRepository diveMeasurementRepository;
    private final DiveSiteRepository diveSiteRepository;

    public StatsDataService(
            final DiveRepository diveRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final DiveSiteRepository diveSiteRepository) {
        this.diveRepository = diveRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveSiteRepository = diveSiteRepository;
    }

    public record RawMainStats(
            long totalDives, Integer maxDiveNumber, Double maxDepth, long uniqueSites) {}

    private record TemperatureStats(Double maxC, Double minC) {
        public Temperature maxTemp() {
            return maxC == null ? null : new Temperature(maxC, CELSIUS);
        }

        public Temperature minTemp() {
            return minC == null ? null : new Temperature(minC, CELSIUS);
        }
    }

    public interface RawGroupCounts<T> {
        T key();

        long totalDives();

        Integer maxNumber();

        Double maxDepth();

        long uniqueSites();
    }

    public record LongGroupCounts(
            Long key, long totalDives, Integer maxNumber, Double maxDepth, long uniqueSites)
            implements RawGroupCounts<Long> {}

    public record IntegerGroupCounts(
            Integer key, long totalDives, Integer maxNumber, Double maxDepth, long uniqueSites)
            implements RawGroupCounts<Integer> {}

    public record StringGroupCounts(
            String key, long totalDives, Integer maxNumber, Double maxDepth, long uniqueSites)
            implements RawGroupCounts<String> {}

    public enum StatsType {
        BUDDY_NAME,
        YEAR,
        DIVE_SITE;
    }

    @Transactional(readOnly = true)
    public Map<Integer, UserDiveStats> getStatsByYear(final User user) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createQuery(IntegerGroupCounts.class);
        final var dive = query.from(DiveEntity.class);
        final var profile = dive.join("profiles");

        final var yearExpr =
                cb.function(
                        "date_part", Double.class, cb.literal("year"), profile.get("profileStart"));

        final var yearIntExpr = cb.toInteger(yearExpr);

        return fetchAndMap(user, query, dive, StatsType.YEAR, yearIntExpr, Integer.class);
    }

    @Transactional(readOnly = true)
    public Map<Long, UserDiveStats> getStatsByDiveSite(final User user) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createQuery(LongGroupCounts.class);
        final var dive = query.from(DiveEntity.class);

        // Join to DiveSite to get the name
        final Join<DiveEntity, DiveSiteEntity> site = dive.join("diveSite", JoinType.INNER);

        return fetchAndMap(user, query, dive, StatsType.DIVE_SITE, site.get("id"), Long.class);
    }

    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<String>> getStatsByBuddy(final User user) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createQuery(StringGroupCounts.class);
        final var dive = query.from(DiveEntity.class);

        // TODO: At the moment, only named buddies
        final var buddy = dive.join("namedBuddies", JoinType.INNER);

        return fetchAndMap(user, query, dive, StatsType.BUDDY_NAME, buddy.get("name"), String.class)
                .entrySet()
                .stream()
                .map(e -> new UserDiveStatsBy<>(e.getKey(), e.getValue()))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private <T, C extends RawGroupCounts<T>> Map<T, UserDiveStats> fetchAndMap(
            final User user,
            final CriteriaQuery<C> query,
            final Root<DiveEntity> dive,
            final StatsType statsType,
            final Expression<T> selection,
            final Class<T> typeToken) {
        final var cb = entityManager.getCriteriaBuilder();
        final var profile = dive.join("profiles", JoinType.LEFT);
        final var measurement = profile.join("measurements", JoinType.LEFT);

        final var diveIdCount = cb.countDistinct(dive.get("id"));
        query.multiselect(
                selection,
                diveIdCount,
                cb.max(dive.get("number")),
                cb.max(measurement.get("depth")),
                cb.countDistinct(dive.get("diveSite").get("id")));

        query.where(cb.equal(dive.get("user").get("id"), user.id()));
        query.groupBy(selection);
        query.orderBy(cb.desc(diveIdCount));

        return entityManager.createQuery(query).getResultList().stream()
                .map(
                        raw ->
                                Pair.of(
                                        typeToken.cast(raw.key()),
                                        assembleFullStats(user, statsType, raw)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    private <T> UserDiveStats assembleFullStats(
            final User user, final StatsType type, final RawGroupCounts<T> raw) {
        // TODO: Use group by instead and don't assume the key by type
        final var maxAndTotalDuration =
                switch (type) {
                    case YEAR -> {
                        final var year = (Integer) raw.key();
                        yield Pair.of(
                                diveRepository
                                        .findMaxDurationByUserIdAndYear(user.id(), year)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO),
                                diveRepository
                                        .findTotalDurationByUserIdAndYear(user.id(), year)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO));
                    }
                    case DIVE_SITE -> {
                        final var siteId = (Long) raw.key();
                        yield Pair.of(
                                diveRepository
                                        .findMaxDurationByUserIdAndDiveSiteId(user.id(), siteId)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO),
                                diveRepository
                                        .findTotalDurationByUserIdAndDiveSiteId(user.id(), siteId)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO));
                    }
                    case BUDDY_NAME -> {
                        final var buddyName = (String) raw.key();
                        // TODO: Fix Named buddies only
                        yield Pair.of(
                                diveRepository
                                        .findMaxDurationByUserIdAndBuddy(user.id(), buddyName)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO),
                                diveRepository
                                        .findTotalDurationByUserIdAndBuddy(user.id(), buddyName)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO));
                    }
                    case null ->
                            Pair.of(
                                    diveRepository
                                            .findMaxDurationByUserId(user.id())
                                            .map(Duration::ofSeconds)
                                            .orElse(Duration.ZERO),
                                    diveRepository
                                            .findTotalDurationByUserId(user.id())
                                            .map(Duration::ofSeconds)
                                            .orElse(Duration.ZERO));
                };

        return new UserDiveStats(
                raw.totalDives(),
                raw.maxNumber() != null ? raw.maxNumber() : -1,
                maxAndTotalDuration.getLeft(),
                raw.maxDepth() != null ? raw.maxDepth() : 0.0,
                maxAndTotalDuration.getRight(),
                0L, // TODO: Buddy count is complex for groups, keeping at 0 or specialized repo
                raw.uniqueSites(),
                null, // TODO: Temperature logic
                null);
    }

    @Transactional(readOnly = true)
    public UserDiveStats computeStatsForUserCriteria(final User user) {
        final var cb = entityManager.getCriteriaBuilder();

        final var main = fetchMainStats(user, cb);
        final var temps = fetchTemperatureStats(user, cb);
        final var buddyCount = countUniqueBuddies(user.id());
        final var maxDuration =
                diveRepository
                        .findMaxDurationByUserId(user.id())
                        .map(Duration::ofSeconds)
                        .orElse(Duration.ZERO);
        final var totalDuration =
                diveRepository
                        .findTotalDurationByUserId(user.id())
                        .map(Duration::ofSeconds)
                        .orElse(Duration.ZERO);

        return new UserDiveStats(
                main.totalDives(),
                main.maxDiveNumber() != null ? main.maxDiveNumber() : -1,
                maxDuration,
                main.maxDepth() != null ? main.maxDepth() : 0.0,
                totalDuration,
                buddyCount,
                main.uniqueSites(),
                temps.maxTemp(),
                temps.minTemp());
    }

    private RawMainStats fetchMainStats(final User user, final CriteriaBuilder cb) {
        final var query = cb.createQuery(RawMainStats.class);
        final var dive = query.from(DiveEntity.class);

        // We join profiles here to get depth/duration
        final Join<DiveEntity, DiveProfileEntity> profile = dive.join("profiles", JoinType.LEFT);
        final Join<DiveProfileEntity, DiveMeasurementEntity> measurement =
                profile.join("measurements", JoinType.LEFT);

        query.multiselect(
                cb.countDistinct(dive.get("id")),
                cb.max(dive.get("number")),
                cb.max(measurement.get("depth")),
                cb.countDistinct(dive.get("diveSite").get("id")));

        query.where(buildPredicates(cb, dive, user));
        return entityManager.createQuery(query).getSingleResult();
    }

    private TemperatureStats fetchTemperatureStats(final User user, final CriteriaBuilder cb) {
        final var query = cb.createQuery(TemperatureStats.class);
        final var measurement = query.from(DiveMeasurementEntity.class);

        final Join<DiveMeasurementEntity, DiveProfileEntity> profile = measurement.join("profile");
        final Join<DiveProfileEntity, DiveEntity> dive = profile.join("dive");

        query.multiselect(
                cb.max(measurement.get("temperatureCelsius")),
                cb.min(measurement.get("temperatureCelsius")));

        query.where(buildPredicates(cb, dive, user));

        // TODO: Empty for now
        return entityManager
                .createQuery(query)
                .getResultStream()
                .findFirst()
                .orElse(new TemperatureStats(null, null));
    }

    private Predicate[] buildPredicates(
            final CriteriaBuilder cb, final From<?, DiveEntity> dive, final User user) {
        final var predicates = new ArrayList<Predicate>();
        predicates.add(cb.equal(dive.get("user").get("id"), user.id()));

        return predicates.toArray(new Predicate[0]);
    }

    private long countUniqueBuddies(final long userId) {
        return diveRepository.countUniqueBuddiesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public UserDiveStats computeStatsForUser(final User user) {
        return new UserDiveStats(
                diveRepository.countByUser_Id(user.id()),
                diveRepository.findMaxDiveNumberByUserId(user.id()).orElse(-1),
                diveRepository
                        .findMaxDurationByUserId(user.id())
                        .map(Duration::ofSeconds)
                        .orElse(Duration.ZERO),
                diveMeasurementRepository.findMaxDepthByUserId(user.id()).orElse(0.0),
                diveRepository
                        .findTotalDurationByUserId(user.id())
                        .map(Duration::ofSeconds)
                        .orElse(Duration.ZERO),
                diveRepository.countUniqueBuddiesByUserId(user.id()),
                diveSiteRepository.countUniqueForUserId(user.id()),
                diveMeasurementRepository
                        .findMaxTemperatureCelsiusByUserId(user.id())
                        .map(d -> new Temperature(d, CELSIUS))
                        .orElse(null),
                diveMeasurementRepository
                        .findMinTemperatureCelsiusByUserId(user.id())
                        .map(d -> new Temperature(d, CELSIUS))
                        .orElse(null));
    }
}

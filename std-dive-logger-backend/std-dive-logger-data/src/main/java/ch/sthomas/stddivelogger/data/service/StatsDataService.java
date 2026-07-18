package ch.sthomas.stddivelogger.data.service;

import static ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature.TemperatureUnit.CELSIUS;

import ch.sthomas.stddivelogger.data.repository.DiveMeasurementRepository;
import ch.sthomas.stddivelogger.data.repository.DiveRepository;
import ch.sthomas.stddivelogger.data.repository.DiveSiteRepository;
import ch.sthomas.stddivelogger.data.repository.TagDefinitionRepository;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.StatsCategoryPoint;
import ch.sthomas.stddivelogger.model.dive.stats.StatsFilters;
import ch.sthomas.stddivelogger.model.dive.stats.StatsGranularity;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeries;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeriesPoint;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.entity.*;
import ch.sthomas.stddivelogger.model.user.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
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
    private final TagDefinitionRepository tagDefinitionRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public StatsDataService(
            final DiveRepository diveRepository,
            final DiveMeasurementRepository diveMeasurementRepository,
            final DiveSiteRepository diveSiteRepository,
            final TagDefinitionRepository tagDefinitionRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.diveRepository = diveRepository;
        this.diveMeasurementRepository = diveMeasurementRepository;
        this.diveSiteRepository = diveSiteRepository;
        this.tagDefinitionRepository = tagDefinitionRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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

    public record BaseConfigurationGroupCounts(
            BaseConfiguration key,
            long totalDives,
            Integer maxNumber,
            Double maxDepth,
            long uniqueSites)
            implements RawGroupCounts<BaseConfiguration> {}

    public enum StatsType {
        BUDDY_NAME,
        DIVE_SITE,
        YEAR,
        CONFIGURATION,
        ;
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

    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<BaseConfiguration>> getStatsByBaseConfiguration(final User user) {
        final var cb = entityManager.getCriteriaBuilder();
        final var query = cb.createQuery(BaseConfigurationGroupCounts.class);
        final var dive = query.from(DiveEntity.class);
        final var configuration =
                dive.<DiveEntity, DiveConfigurationEntity>join("configuration", JoinType.INNER);

        return fetchAndMap(
                        user,
                        query,
                        dive,
                        StatsType.CONFIGURATION,
                        configuration.get("baseConfiguration"),
                        BaseConfiguration.class)
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
                    case CONFIGURATION -> {
                        final var configuration = (BaseConfiguration) raw.key();
                        yield Pair.of(
                                diveRepository
                                        .findMaxDurationByUserIdAndConfiguration_BaseConfiguration(
                                                user.id(), configuration)
                                        .map(Duration::ofSeconds)
                                        .orElse(Duration.ZERO),
                                diveRepository
                                        .findTotalDurationByUserIdAndConfiguration_BaseConfiguration(
                                                user.id(), configuration)
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

    /**
     * Returns stats for each tag that appears on at least one of the user's dives,
     * sorted by dive count descending.
     */
    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<TagDefinition>> getStatsByTag(final User user) {
        // Aggregate: [tagId, diveCount, maxDiveNumber, uniqueSites] per tag
        final List<Object[]> rows = entityManager.createQuery(
                        """
                        SELECT dt.tag.id, COUNT(DISTINCT d.id), MAX(d.number),
                               COUNT(DISTINCT d.diveSite.id)
                        FROM DiveEntity d
                        JOIN d.tags dt
                        WHERE d.user.id = :userId AND dt.dismissed = false
                        GROUP BY dt.tag.id
                        ORDER BY COUNT(DISTINCT d.id) DESC
                        """, Object[].class)
                .setParameter("userId", user.id())
                .getResultList();

        if (rows.isEmpty()) {
            return List.of();
        }

        // Look up TagDefinition records (with diveCount) for all returned tag IDs
        final var tagIds = rows.stream().map(r -> (Long) r[0]).toList();
        final var tagMap = tagDefinitionRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e));

        return rows.stream()
                .map(row -> {
                    final long tagId       = (Long)    row[0];
                    final long diveCount   = (Long)    row[1];
                    final int  maxNumber   = row[2] != null ? ((Number) row[2]).intValue() : -1;
                    final long uniqueSites = (Long)    row[3];

                    final var maxDuration   = diveRepository.findMaxDurationByUserIdAndTagId(user.id(), tagId)
                            .map(Duration::ofSeconds).orElse(Duration.ZERO);
                    final var totalDuration = diveRepository.findTotalDurationByUserIdAndTagId(user.id(), tagId)
                            .map(Duration::ofSeconds).orElse(Duration.ZERO);
                    final var maxDepth      = diveMeasurementRepository.findMaxDepthByUserIdAndTagId(user.id(), tagId)
                            .orElse(0.0);

                    final var stats = new UserDiveStats(
                            diveCount, maxNumber, maxDuration, maxDepth, totalDuration,
                            0L, uniqueSites, null, null);

                    final var tagEntity = tagMap.get(tagId);
                    final var tagDef = tagEntity != null
                            ? tagEntity.toRecord(diveCount)
                            : new TagDefinition(tagId, "Unknown", null, null, diveCount);

                    return new UserDiveStatsBy<>(tagDef, stats);
                })
                .toList();
    }

    /**
     * Computes stats for dives matching ALL of the specified tag IDs (AND semantics).
     * Returns null if the tag list is empty or no dives match.
     */
    @Transactional(readOnly = true)
    public UserDiveStats computeStatsForTagFilter(final User user, final Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return null;
        }
        final var diveIds = diveRepository.findDiveIdsByTagsAndUserId(user.id(), tagIds, tagIds.size());
        if (diveIds.isEmpty()) {
            return new UserDiveStats(0, -1, Duration.ZERO, 0.0, Duration.ZERO, 0L, 0L, null, null);
        }

        // Aggregate main counts from the matched dive set directly via JPQL
        final Object[] main = (Object[]) entityManager.createQuery(
                        """
                        SELECT COUNT(DISTINCT d.id), MAX(d.number), COUNT(DISTINCT d.diveSite.id)
                        FROM DiveEntity d
                        WHERE d.id IN :diveIds
                        """)
                .setParameter("diveIds", diveIds)
                .getSingleResult();

        final long diveCount   = (Long)    main[0];
        final int  maxNumber   = main[1] != null ? ((Number) main[1]).intValue() : -1;
        final long uniqueSites = (Long)    main[2];

        final var maxDuration   = diveRepository.findMaxDurationByDiveIds(diveIds)
                .map(Duration::ofSeconds).orElse(Duration.ZERO);
        final var totalDuration = diveRepository.findTotalDurationByDiveIds(diveIds)
                .map(Duration::ofSeconds).orElse(Duration.ZERO);
        final var maxDepth      = diveMeasurementRepository.findMaxDepthByDiveIds(diveIds)
                .orElse(0.0);

        return new UserDiveStats(diveCount, maxNumber, maxDuration, maxDepth, totalDuration,
                0L, uniqueSites, null, null);
    }

    private record BucketSql(String selectExpr, String groupByExpr, String diveIdExpr) {}

    /**
     * The bucket expression is chosen from this fixed whitelist only (never from user input), so
     * interpolating it directly into the SQL text below is safe. {@code diveIdExpr} only resolves
     * to a real dive id for {@code PER_DIVE} (where a bucket is exactly one dive); it's a SQL
     * NULL literal for the other granularities, where a bucket spans many dives.
     */
    private static BucketSql bucketSql(final StatsGranularity granularity) {
        return switch (granularity) {
            case PER_DIVE ->
                    new BucketSql("fd.dive_start", "fd.dive_id, fd.dive_start", "fd.dive_id");
            case WEEK ->
                    new BucketSql(
                            "date_trunc('week', fd.dive_start)",
                            "date_trunc('week', fd.dive_start)",
                            "NULL::bigint");
            case MONTH ->
                    new BucketSql(
                            "date_trunc('month', fd.dive_start)",
                            "date_trunc('month', fd.dive_start)",
                            "NULL::bigint");
            case QUARTER ->
                    new BucketSql(
                            "date_trunc('quarter', fd.dive_start)",
                            "date_trunc('quarter', fd.dive_start)",
                            "NULL::bigint");
            case YEAR ->
                    new BucketSql(
                            "date_trunc('year', fd.dive_start)",
                            "date_trunc('year', fd.dive_start)",
                            "NULL::bigint");
        };
    }

    /**
     * Builds the shared "filtered_dives" CTE (as a WITH-clause prefix, including the trailing
     * closing paren) plus the bound parameters for every filter dimension that was supplied.
     * Reused verbatim by the main aggregation query and both category-breakdown queries.
     */
    private Pair<String, MapSqlParameterSource> filteredDivesCte(
            final long userId, final StatsFilters filters) {
        final var params = new MapSqlParameterSource().addValue("userId", userId);
        final var where = new StringBuilder("d.fk_diver_id = :userId");

        if (filters.diveSiteId() != null) {
            where.append(" AND d.dive_site = :diveSiteId");
            params.addValue("diveSiteId", filters.diveSiteId());
        }
        if (filters.suitId() != null) {
            where.append(" AND dc.fk_suit_id = :suitId");
            params.addValue("suitId", filters.suitId());
        }
        if (filters.baseConfiguration() != null) {
            where.append(" AND dc.base_configuration = :baseConfiguration");
            params.addValue("baseConfiguration", filters.baseConfiguration().name());
        }
        if (filters.query() != null && !filters.query().isBlank()) {
            where.append(" AND d.dive_identifier ILIKE :query");
            params.addValue("query", "%" + filters.query().trim() + "%");
        }
        if (filters.tagIds() != null && !filters.tagIds().isEmpty()) {
            where.append(
                    """
                     AND d.pk_dive_id IN (
                        SELECT dt.fk_dive_id FROM t_dive_tags dt
                        WHERE dt.fk_tag_id IN (:tagIds) AND dt.dismissed = false
                        GROUP BY dt.fk_dive_id
                        HAVING COUNT(DISTINCT dt.fk_tag_id) = :tagCount
                    )""");
            params.addValue("tagIds", filters.tagIds());
            params.addValue("tagCount", (long) filters.tagIds().size());
        }

        final var cte =
                """
                WITH filtered_dives AS (
                    SELECT
                        d.pk_dive_id AS dive_id,
                        ds.dive_start,
                        ds.max_depth,
                        ds.avg_depth,
                        ds.duration_seconds,
                        gc.rmv_liters,
                        v.visibility_meters,
                        dc.weight_kg,
                        dc.fk_suit_id,
                        dc.base_configuration
                    FROM t_dives d
                    JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                    LEFT JOIN t_dive_gas_consumption gc ON gc.fk_dive_id = d.pk_dive_id
                    LEFT JOIN t_dive_visibility v ON v.fk_dive_id = d.pk_dive_id
                    LEFT JOIN t_dive_configuration dc ON dc.fk_dive_id = d.pk_dive_id
                    WHERE
                """
                        + where
                        + "\n)\n";

        return Pair.of(cte, params);
    }

    private static Double nullableDouble(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Buckets the user's dives (after applying {@code filters}) by {@code granularity} and
     * returns per-bucket aggregates for every numeric metric, plus a suit-usage and
     * base-configuration-usage breakdown (dive count per category per bucket).
     */
    @Transactional(readOnly = true)
    public StatsTimeSeries getTimeSeries(
            final User user, final StatsGranularity granularity, final StatsFilters filters) {
        final var cteAndParams = filteredDivesCte(user.id(), filters);
        final var filteredDivesCte = cteAndParams.getLeft();
        final var params = cteAndParams.getRight();
        final var bucket = bucketSql(granularity);

        final var mainSql =
                filteredDivesCte
                        + """
                        , end_cns AS (
                            SELECT DISTINCT ON (dp.fk_dive_id) dp.fk_dive_id AS dive_id, dm.cns
                            FROM t_dive_measurements dm
                            JOIN t_dive_profiles dp ON dm.fk_dive_profile_id = dp.pk_dive_profile_id
                            WHERE dm.cns IS NOT NULL
                              AND dp.fk_dive_id IN (SELECT dive_id FROM filtered_dives)
                            ORDER BY dp.fk_dive_id, dp.dive_profile_end DESC, dm.time DESC
                        ),
                        avg_temp AS (
                            SELECT dp.fk_dive_id AS dive_id, AVG(dm.temperature_celsius) AS avg_temp_celsius
                            FROM t_dive_measurements dm
                            JOIN t_dive_profiles dp ON dm.fk_dive_profile_id = dp.pk_dive_profile_id
                            WHERE dm.temperature_celsius IS NOT NULL
                              AND dp.fk_dive_id IN (SELECT dive_id FROM filtered_dives)
                            GROUP BY dp.fk_dive_id
                        )
                        SELECT
                        """
                        + bucket.selectExpr()
                        + " AS bucket_start,\n"
                        + bucket.diveIdExpr()
                        + " AS dive_id_col,\n"
                        + """
                                COUNT(*) AS dive_count,
                                AVG(fd.rmv_liters) AS avg_rmv_liters,
                                MAX(fd.max_depth) AS max_depth,
                                AVG(fd.avg_depth) AS avg_depth,
                                COALESCE(SUM(fd.duration_seconds), 0) AS total_duration_seconds,
                                COALESCE(MAX(fd.duration_seconds), 0) AS max_duration_seconds,
                                AVG(ec.cns) AS avg_end_cns,
                                AVG(at.avg_temp_celsius) AS avg_temperature_celsius,
                                AVG(fd.visibility_meters) AS avg_visibility_meters,
                                AVG(fd.weight_kg) AS avg_weight_kg
                            FROM filtered_dives fd
                            LEFT JOIN end_cns ec ON ec.dive_id = fd.dive_id
                            LEFT JOIN avg_temp at ON at.dive_id = fd.dive_id
                            GROUP BY
                        """
                        + bucket.groupByExpr()
                        + "\nORDER BY bucket_start";

        final var points =
                namedParameterJdbcTemplate.query(
                        mainSql,
                        params,
                        (rs, rowNum) ->
                                new StatsTimeSeriesPoint(
                                        rs.getTimestamp("bucket_start").toInstant(),
                                        nullableLong(rs, "dive_id_col"),
                                        rs.getLong("dive_count"),
                                        nullableDouble(rs, "avg_rmv_liters"),
                                        nullableDouble(rs, "max_depth"),
                                        nullableDouble(rs, "avg_depth"),
                                        rs.getLong("total_duration_seconds"),
                                        rs.getLong("max_duration_seconds"),
                                        nullableDouble(rs, "avg_end_cns"),
                                        nullableDouble(rs, "avg_temperature_celsius"),
                                        nullableDouble(rs, "avg_visibility_meters"),
                                        nullableDouble(rs, "avg_weight_kg")));

        final var suitUsage = categoryBreakdown(filteredDivesCte, bucket, params, true);
        final var baseConfigurationUsage = categoryBreakdown(filteredDivesCte, bucket, params, false);

        return new StatsTimeSeries(points, suitUsage, baseConfigurationUsage);
    }

    private List<StatsCategoryPoint> categoryBreakdown(
            final String filteredDivesCte,
            final BucketSql bucket,
            final MapSqlParameterSource params,
            final boolean bySuit) {
        final var categoryExpr =
                bySuit
                        ? "COALESCE(s.type::text || COALESCE(' ' || s.thickness_mm::text || 'mm', ''), 'No suit')"
                        : "COALESCE(fd.base_configuration, 'UNKNOWN')";
        final var joinClause = bySuit ? "LEFT JOIN t_suits s ON s.pk_suit_id = fd.fk_suit_id" : "";

        final var sql =
                filteredDivesCte
                        + "SELECT "
                        + bucket.selectExpr()
                        + " AS bucket_start, "
                        + categoryExpr
                        + " AS category, COUNT(*) AS dive_count\n"
                        + "FROM filtered_dives fd\n"
                        + joinClause
                        + "\nGROUP BY "
                        + bucket.groupByExpr()
                        + ", category\nORDER BY bucket_start";

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) ->
                        new StatsCategoryPoint(
                                rs.getTimestamp("bucket_start").toInstant(),
                                rs.getString("category"),
                                rs.getLong("dive_count")));
    }
}

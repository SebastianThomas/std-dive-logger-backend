package ch.sthomas.stddivelogger.data.service;

import static ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature.TemperatureUnit.CELSIUS;

import ch.sthomas.stddivelogger.data.repository.TagDefinitionRepository;
import ch.sthomas.stddivelogger.model.dive.TagDefinition;
import ch.sthomas.stddivelogger.model.dive.gear.BaseConfiguration;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.dive.stats.StatsBreakdownDimension;
import ch.sthomas.stddivelogger.model.dive.stats.StatsFilters;
import ch.sthomas.stddivelogger.model.dive.stats.StatsGranularity;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeries;
import ch.sthomas.stddivelogger.model.dive.stats.StatsTimeSeriesPoint;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStats;
import ch.sthomas.stddivelogger.model.dive.stats.UserDiveStatsBy;
import ch.sthomas.stddivelogger.model.user.User;

import org.apache.commons.lang3.tuple.Pair;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every method here does its full aggregation - counts, durations, depths, unique-site counts,
 * buddy counts, temperature min/max - in a single grouped SQL query per call, rather than one query
 * for the group list plus N more queries per group (the previous design). Three CTEs are shared
 * across almost every method:
 *
 * <ul>
 *   <li>{@code dives} - one row per dive with its number/site/duration/depth/year, already scoped
 *       to the user.
 *   <li>{@code buddies} - one row per (dive, buddy name) pair, deduplicating named buddies and
 *       linked-user buddies from both link directions (mirrors the UNION this codebase has always
 *       used for a user-wide buddy count, just scoped down to a specific dive set via a join
 *       instead of via {@code IN (:diveIds)}).
 *   <li>{@code temps} - per-dive max/min measured temperature.
 * </ul>
 *
 * A breakdown (by year/site/buddy/configuration/tag) then joins its own grouping table (or column
 * already on {@code dives}) against these three CTEs and aggregates with one {@code GROUP BY}.
 */
@Service
public class StatsDataService {

    private final TagDefinitionRepository tagDefinitionRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public StatsDataService(
            final TagDefinitionRepository tagDefinitionRepository,
            final NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.tagDefinitionRepository = tagDefinitionRepository;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    /**
     * One row per dive of the user, with everything a breakdown needs except buddy/temperature
     * (those come from the {@link #BUDDIES_CTE}/{@link #TEMPS_CTE} below - a dive can have many
     * buddies/measurements, so they can't live on this 1-row-per-dive CTE without duplicating every
     * other column per buddy/measurement).
     */
    private static final String DIVES_CTE =
            """
            dives AS (
                SELECT d.pk_dive_id AS dive_id, d.dive_number, d.dive_site AS site_id,
                       ds.duration_seconds, ds.max_depth, ds.dive_start,
                       dc.base_configuration, COALESCE(site.site_type, 'UNSPECIFIED') AS site_type
                FROM t_dives d
                JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                JOIN t_dive_configuration dc ON dc.fk_dive_id = d.pk_dive_id
                JOIN t_dive_site site ON site.pk_dive_site_id = d.dive_site
                WHERE d.fk_diver_id = :userId
            )
            """;

    /**
     * One row per (dive id, buddy name), deduplicated by name per dive via the outer query's {@code
     * COUNT(DISTINCT b.name)} - not here, since a dive could otherwise list the same person twice
     * (e.g. a named buddy who's also a linked user).
     */
    private static final String BUDDIES_CTE =
            """
            buddies AS (
                SELECT b.fk_dive_id AS dive_id, b.name AS name
                FROM t_dive_buddy_name b
                JOIN t_dives d ON d.pk_dive_id = b.fk_dive_id AND d.fk_diver_id = :userId
                UNION
                SELECT d_this.pk_dive_id AS dive_id, u.name AS name
                FROM t_dives d_this
                JOIN t_dive_buddy tb ON tb.fk_dive_id = d_this.pk_dive_id AND d_this.fk_diver_id = :userId
                JOIN t_dives d_other ON d_other.pk_dive_id = tb.fk_buddy_dive_id
                JOIN t_users u ON u.pk_user_id = d_other.fk_diver_id
                UNION
                SELECT d_this.pk_dive_id AS dive_id, u.name AS name
                FROM t_dives d_this
                JOIN t_dive_buddy tb ON tb.fk_buddy_dive_id = d_this.pk_dive_id AND d_this.fk_diver_id = :userId
                JOIN t_dives d_other ON d_other.pk_dive_id = tb.fk_dive_id
                JOIN t_users u ON u.pk_user_id = d_other.fk_diver_id
            )
            """;

    private static final String TEMPS_CTE =
            """
            temps AS (
                SELECT dp.fk_dive_id AS dive_id,
                       MAX(dm.temperature_celsius) AS max_temp,
                       MIN(dm.temperature_celsius) AS min_temp
                FROM t_dive_measurements dm
                JOIN t_dive_profiles dp ON dp.pk_dive_profile_id = dm.fk_dive_profile_id
                JOIN t_dives d ON d.pk_dive_id = dp.fk_dive_id AND d.fk_diver_id = :userId
                GROUP BY dp.fk_dive_id
            )
            """;

    /**
     * Every metric except the group key and buddy count - those two differ per query (the group key
     * obviously; buddy count either comes from a {@code buddies} join, or is meaningless and
     * omitted, as for the by-buddy breakdown itself).
     */
    private static final String AGGREGATE_SELECT_LIST =
            """
            COUNT(DISTINCT dv.dive_id) AS dive_count,
            MAX(dv.dive_number) AS max_number,
            MAX(dv.max_depth) AS max_depth,
            COUNT(DISTINCT dv.site_id) AS unique_sites,
            MAX(dv.duration_seconds) AS max_duration,
            COALESCE(SUM(dv.duration_seconds), 0) AS total_duration
            """;

    private static @Nullable Double nullableDouble(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static @Nullable Long nullableLong(final ResultSet rs, final String column)
            throws SQLException {
        final var value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static @Nullable Temperature temperatureOrNull(final ResultSet rs, final String column)
            throws SQLException {
        final var value = nullableDouble(rs, column);
        return value == null ? null : new Temperature(value, CELSIUS);
    }

    /**
     * Maps every column {@link #AGGREGATE_SELECT_LIST} plus a {@code buddy_count} and {@code
     * max_temp}/{@code min_temp} pair into a {@link UserDiveStats}. Used by every method below -
     * grouped or not, since a single-row (ungrouped) aggregate query uses exactly the same column
     * set as one row of a grouped one.
     */
    private static UserDiveStats mapAggregateRow(final ResultSet rs) throws SQLException {
        final var maxNumber = nullableLong(rs, "max_number");
        final var maxDuration = nullableLong(rs, "max_duration");
        return new UserDiveStats(
                rs.getLong("dive_count"),
                maxNumber != null ? maxNumber : -1,
                Duration.ofSeconds(maxDuration != null ? maxDuration : 0),
                nullableDouble(rs, "max_depth") != null ? rs.getDouble("max_depth") : 0.0,
                Duration.ofSeconds(rs.getLong("total_duration")),
                rs.getLong("buddy_count"),
                rs.getLong("unique_sites"),
                temperatureOrNull(rs, "max_temp"),
                temperatureOrNull(rs, "min_temp"));
    }

    @Transactional(readOnly = true)
    public Map<Integer, UserDiveStats> getStatsByYear(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT EXTRACT(YEAR FROM dv.dive_start)::int AS grp,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM dives dv
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        GROUP BY EXTRACT(YEAR FROM dv.dive_start)
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        final var result = new LinkedHashMap<Integer, UserDiveStats>();
        namedParameterJdbcTemplate
                .query(sql, params, (rs, rowNum) -> Pair.of(rs.getInt("grp"), mapAggregateRow(rs)))
                .forEach(p -> result.put(p.getKey(), p.getValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, UserDiveStats> getStatsByDiveSite(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT dv.site_id AS grp,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM dives dv
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        GROUP BY dv.site_id
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        final var result = new LinkedHashMap<Long, UserDiveStats>();
        namedParameterJdbcTemplate
                .query(sql, params, (rs, rowNum) -> Pair.of(rs.getLong("grp"), mapAggregateRow(rs)))
                .forEach(p -> result.put(p.getKey(), p.getValue()));
        return result;
    }

    /**
     * Named buddies only (matches this breakdown's long-standing scope - the caller, {@code
     * StatsService.getStatsForUserByBuddy}, already discards {@code nrOfBuddies} on the result, so
     * a "how many *other* buddies did I have on dives with this buddy" figure was never meaningful
     * here - the {@link #BUDDIES_CTE} union-with-linked-users is deliberately not used as the
     * grouping source, only named buddies are, unlike every other breakdown's buddy *count*).
     */
    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<String>> getStatsByBuddy(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT bn.name AS grp,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM t_dive_buddy_name bn
                        JOIN dives dv ON dv.dive_id = bn.fk_dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        GROUP BY bn.name
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        return namedParameterJdbcTemplate
                .query(
                        sql,
                        params,
                        (rs, rowNum) ->
                                new UserDiveStatsBy<>(
                                        rs.getString("grp"), mapAggregateRowNoBuddyCount(rs)))
                .stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<BaseConfiguration>> getStatsByBaseConfiguration(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT dv.base_configuration AS grp,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM dives dv
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        GROUP BY dv.base_configuration
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        return namedParameterJdbcTemplate
                .query(
                        sql,
                        params,
                        (rs, rowNum) ->
                                new UserDiveStatsBy<>(
                                        BaseConfiguration.valueOf(rs.getString("grp")),
                                        mapAggregateRow(rs)))
                .stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * Groups by {@code site.site_type} (see {@link #DIVES_CTE}, {@code UNSPECIFIED} when the site
     * hasn't had a type set yet) - a string key rather than an enum, so an unset/unknown type never
     * fails to deserialize even if the enum's members change later.
     */
    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<String>> getStatsBySiteType(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT dv.site_type AS grp,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM dives dv
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        GROUP BY dv.site_type
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        return namedParameterJdbcTemplate
                .query(
                        sql,
                        params,
                        (rs, rowNum) ->
                                new UserDiveStatsBy<>(rs.getString("grp"), mapAggregateRow(rs)))
                .stream()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * Same as {@link #mapAggregateRow} but for the by-buddy breakdown's query, which has no {@code
     * buddy_count} column (see {@link #getStatsByBuddy}'s doc) - 0 there simply matches what {@link
     * #mapAggregateRow} would have produced anyway once the caller discards it.
     */
    private static UserDiveStats mapAggregateRowNoBuddyCount(final ResultSet rs)
            throws SQLException {
        final var maxNumber = nullableLong(rs, "max_number");
        final var maxDuration = nullableLong(rs, "max_duration");
        return new UserDiveStats(
                rs.getLong("dive_count"),
                maxNumber != null ? maxNumber : -1,
                Duration.ofSeconds(maxDuration != null ? maxDuration : 0),
                nullableDouble(rs, "max_depth") != null ? rs.getDouble("max_depth") : 0.0,
                Duration.ofSeconds(rs.getLong("total_duration")),
                0L,
                rs.getLong("unique_sites"),
                temperatureOrNull(rs, "max_temp"),
                temperatureOrNull(rs, "min_temp"));
    }

    /**
     * Returns stats for each tag that appears on at least one of the user's dives, sorted by dive
     * count descending.
     */
    @Transactional(readOnly = true)
    public List<UserDiveStatsBy<TagDefinition>> getStatsByTag(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT dt.fk_tag_id AS tag_id,
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM t_dive_tags dt
                        JOIN dives dv ON dv.dive_id = dt.fk_dive_id
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        WHERE dt.dismissed = false
                        GROUP BY dt.fk_tag_id
                        ORDER BY dive_count DESC
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        final var rows =
                namedParameterJdbcTemplate.query(
                        sql,
                        params,
                        (rs, rowNum) -> Pair.of(rs.getLong("tag_id"), mapAggregateRow(rs)));
        if (rows.isEmpty()) {
            return List.of();
        }

        final var tagIds = rows.stream().map(Pair::getKey).toList();
        final var tagMap =
                tagDefinitionRepository.findAllById(tagIds).stream()
                        .collect(Collectors.toMap(e -> e.getId(), e -> e));

        return rows.stream()
                .map(
                        row -> {
                            final var tagId = row.getKey();
                            final var stats = row.getValue();
                            final var tagEntity = tagMap.get(tagId);
                            final var tagDef =
                                    tagEntity != null
                                            ? tagEntity.toRecord(stats.diveCount())
                                            : new TagDefinition(
                                                    tagId,
                                                    "Unknown",
                                                    null,
                                                    null,
                                                    stats.diveCount());
                            return new UserDiveStatsBy<>(tagDef, stats);
                        })
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    /**
     * Computes stats for dives matching ALL of the specified tag IDs (AND semantics). Returns null
     * if the tag list is empty. If no dives match, a single row still comes back from the query
     * (aggregates over an empty joined set), producing the correct all-zero/all-null {@link
     * UserDiveStats} without any special-casing here.
     */
    @Transactional(readOnly = true)
    public @Nullable UserDiveStats computeStatsForTagFilter(
            final User user, final @Nullable Collection<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return null;
        }
        final var sql =
                """
                WITH matched AS (
                    SELECT dt.fk_dive_id AS dive_id
                    FROM t_dive_tags dt
                    WHERE dt.fk_tag_id IN (:tagIds) AND dt.dismissed = false
                    GROUP BY dt.fk_dive_id
                    HAVING COUNT(DISTINCT dt.fk_tag_id) = :tagCount
                ), """
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM matched m
                        JOIN dives dv ON dv.dive_id = m.dive_id
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        """;
        final var params =
                new MapSqlParameterSource("userId", user.id())
                        .addValue("tagIds", tagIds)
                        .addValue("tagCount", (long) tagIds.size());
        return namedParameterJdbcTemplate.queryForObject(
                sql, params, (rs, rowNum) -> mapAggregateRow(rs));
    }

    /** Overall stats across every dive of the user - one query, no grouping. */
    @Transactional(readOnly = true)
    public UserDiveStats computeStatsForUser(final User user) {
        final var sql =
                "WITH "
                        + DIVES_CTE
                        + ", "
                        + BUDDIES_CTE
                        + ", "
                        + TEMPS_CTE
                        + """
                        SELECT
                        """
                        + AGGREGATE_SELECT_LIST
                        + """
                        , COUNT(DISTINCT b.name) AS buddy_count,
                          MAX(t.max_temp) AS max_temp,
                          MIN(t.min_temp) AS min_temp
                        FROM dives dv
                        LEFT JOIN buddies b ON b.dive_id = dv.dive_id
                        LEFT JOIN temps t ON t.dive_id = dv.dive_id
                        """;
        final var params = new MapSqlParameterSource("userId", user.id());
        return namedParameterJdbcTemplate.queryForObject(
                sql, params, (rs, rowNum) -> mapAggregateRow(rs));
    }

    private record BucketSql(String selectExpr, String groupByExpr, String diveIdExpr) {}

    /**
     * The bucket expression is chosen from this fixed whitelist only (never from user input), so
     * interpolating it directly into the SQL text below is safe. {@code diveIdExpr} only resolves
     * to a real dive id for {@code PER_DIVE} (where a bucket is exactly one dive); it's a SQL NULL
     * literal for the other granularities, where a bucket spans many dives.
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
     * closing paren) plus the bound parameters for every filter dimension that was supplied. Reused
     * verbatim by the main aggregation query and both category-breakdown queries.
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
        if (filters.ccrUnitId() != null) {
            where.append(" AND dc.fk_ccr_unit_id = :ccrUnitId");
            params.addValue("ccrUnitId", filters.ccrUnitId());
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
                        NULLIF(gc.rmv_liters, 0) AS rmv_liters,
                        v.visibility_meters,
                        dc.weight_kg,
                        dc.fk_suit_id,
                        dc.fk_ccr_unit_id,
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

    /**
     * Shared by the main aggregate query and the breakdown query — identical metric columns either
     * way.
     */
    private static final String METRIC_SELECT_LIST =
            """
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
            """;

    /**
     * Adds the {@code end_cns}/{@code avg_temp} CTEs (shared by the main query and the breakdown
     * query) on top of the already-built {@code filtered_dives} CTE.
     */
    private String metricCtePreamble(final String filteredDivesCte) {
        return filteredDivesCte
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
                """;
    }

    private StatsTimeSeriesPoint mapPoint(final ResultSet rs, final boolean withCategory)
            throws SQLException {
        return new StatsTimeSeriesPoint(
                rs.getTimestamp("bucket_start").toInstant(),
                withCategory ? null : nullableLong(rs, "dive_id_col"),
                withCategory ? rs.getString("category") : null,
                rs.getLong("dive_count"),
                nullableDouble(rs, "avg_rmv_liters"),
                nullableDouble(rs, "max_depth"),
                nullableDouble(rs, "avg_depth"),
                rs.getLong("total_duration_seconds"),
                rs.getLong("max_duration_seconds"),
                nullableDouble(rs, "avg_end_cns"),
                nullableDouble(rs, "avg_temperature_celsius"),
                nullableDouble(rs, "avg_visibility_meters"),
                nullableDouble(rs, "avg_weight_kg"));
    }

    /**
     * Buckets the user's dives (after applying {@code filters}) by {@code granularity} and returns
     * per-bucket aggregates for every numeric metric. When {@code breakdownBy} is non-null, also
     * returns the same set of metrics grouped by (bucket, category) for that dimension, so any
     * selected metric can be split into one line per suit / base configuration.
     */
    @Transactional(readOnly = true)
    public StatsTimeSeries getTimeSeries(
            final User user,
            final StatsGranularity granularity,
            final StatsFilters filters,
            final @Nullable StatsBreakdownDimension breakdownBy) {
        final var cteAndParams = filteredDivesCte(user.id(), filters);
        final var filteredDivesCte = cteAndParams.getLeft();
        final var params = cteAndParams.getRight();
        final var bucket = bucketSql(granularity);
        final var preamble = metricCtePreamble(filteredDivesCte);

        final var mainSql =
                preamble
                        + "SELECT\n"
                        + bucket.selectExpr()
                        + " AS bucket_start,\n"
                        + bucket.diveIdExpr()
                        + " AS dive_id_col,\n"
                        + METRIC_SELECT_LIST
                        + """
                            FROM filtered_dives fd
                            LEFT JOIN end_cns ec ON ec.dive_id = fd.dive_id
                            LEFT JOIN avg_temp at ON at.dive_id = fd.dive_id
                            GROUP BY
                        """
                        + bucket.groupByExpr()
                        + "\nORDER BY bucket_start";

        final var points =
                namedParameterJdbcTemplate.query(
                        mainSql, params, (rs, rowNum) -> mapPoint(rs, false));

        final var breakdown =
                breakdownBy == null
                        ? List.<StatsTimeSeriesPoint>of()
                        : categoryBreakdown(preamble, bucket, params, breakdownBy);

        return new StatsTimeSeries(points, breakdown);
    }

    private static final List<String> CCR_BASE_CONFIGURATION_NAMES =
            Arrays.stream(BaseConfiguration.values())
                    .filter(BaseConfiguration::isCcr)
                    .map(Enum::name)
                    .toList();

    private List<StatsTimeSeriesPoint> categoryBreakdown(
            final String preamble,
            final BucketSql bucket,
            final MapSqlParameterSource params,
            final StatsBreakdownDimension dimension) {
        final var categoryExpr =
                switch (dimension) {
                    case SUIT ->
                            "COALESCE(s.type::text || COALESCE(' ' || s.thickness_mm::text || 'mm', ''), 'No suit')";
                    case CCR_UNIT -> "COALESCE(cu.name, 'No CCR unit')";
                    case BASE_CONFIGURATION -> "COALESCE(fd.base_configuration, 'UNKNOWN')";
                };
        final var joinClause =
                switch (dimension) {
                    case SUIT -> "LEFT JOIN t_suits s ON s.pk_suit_id = fd.fk_suit_id\n";
                    case CCR_UNIT ->
                            "LEFT JOIN t_ccr_units cu ON cu.pk_ccr_unit_id = fd.fk_ccr_unit_id\n";
                    case BASE_CONFIGURATION -> "";
                };
        // A CCR unit only ever makes sense on a CCR-configured dive — scope this specific
        // breakdown to those, so it isn't swamped by every other (non-CCR) dive all lumped into
        // one "No CCR unit" bucket.
        final var whereClause = new StringBuilder();
        if (dimension == StatsBreakdownDimension.CCR_UNIT) {
            whereClause.append("WHERE fd.base_configuration IN (:ccrBaseConfigurations)\n");
            params.addValue("ccrBaseConfigurations", CCR_BASE_CONFIGURATION_NAMES);
        }

        final var sql =
                preamble
                        + "SELECT\n"
                        + bucket.selectExpr()
                        + " AS bucket_start,\n"
                        + categoryExpr
                        + " AS category,\n"
                        + METRIC_SELECT_LIST
                        + "FROM filtered_dives fd\n"
                        + joinClause
                        + "LEFT JOIN end_cns ec ON ec.dive_id = fd.dive_id\n"
                        + "LEFT JOIN avg_temp at ON at.dive_id = fd.dive_id\n"
                        + whereClause
                        + "GROUP BY\n"
                        + bucket.groupByExpr()
                        + ", category\nORDER BY bucket_start";

        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> mapPoint(rs, true));
    }
}

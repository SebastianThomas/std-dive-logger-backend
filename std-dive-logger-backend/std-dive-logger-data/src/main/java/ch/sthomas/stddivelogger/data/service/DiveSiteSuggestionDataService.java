package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.model.dive.DiveSite;
import ch.sthomas.stddivelogger.model.dive.DiveSiteSuggestion;
import ch.sthomas.stddivelogger.model.dive.DiveSiteType;
import ch.sthomas.stddivelogger.model.dive.conditions.WaterType;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Scores every dive site with data against one diver's own history: worth a revisit, better
 * visibility than its neighbours, popular lately, an underrated find, or a good depth match - and
 * writes a short reason for each factor that actually contributed. Reads the bulk-refreshed {@code
 * t_dive_site_stats} (see {@link DiveSiteStatsDataService}); the per-diver part runs on demand.
 */
@Service
public class DiveSiteSuggestionDataService {

    private static final int NEIGHBORHOOD_METERS = 50_000;
    private static final int RECENT_VISIT_EXCLUSION_DAYS = 21;
    private static final double REVISIT_DAYS_PER_POINT = 30.0;
    private static final double REVISIT_CAP = 6.0;
    private static final double VISIBILITY_EDGE_PER_METER = 0.4;
    private static final double VISIBILITY_EDGE_CAP = 4.0;
    private static final double VISIBILITY_EDGE_FLOOR = -1.5;
    private static final double VISIBILITY_EDGE_MENTION_DELTA_M = 2.0;
    private static final double POPULARITY_CAP = 5.0;
    private static final int UNDERRATED_MAX_TOTAL_DIVES = 3;
    private static final double UNDERRATED_MIN_HIGHLIGHT_RATE = 0.3;
    private static final double UNDERRATED_MIN_VISIBILITY_M = 15.0;
    private static final double UNDERRATED_BONUS = 3.0;
    private static final double DEPTH_WIDE_RANGE_M = 15.0;
    private static final double DEPTH_COMFORTABLE_FACTOR = 1.15;
    private static final double DEPTH_EXPERIENCED_FACTOR = 2.0;
    private static final double DEPTH_NOVICE_FACTOR = 1.5;
    private static final double DEPTH_MATCH_BONUS = 2.0;
    private static final double DEPTH_MISMATCH_PENALTY = -4.0;
    private static final double DEPTH_MILD_CAUTION_PENALTY = -1.0;
    private static final int EXPERIENCED_CERT_COUNT = 2;
    private static final double EXPERIENCED_DEPTH_M = 30.0;
    private static final double DEFAULT_MAX_DISTANCE_KM = 50.0;
    private static final double PROXIMITY_DECAY_PER_KM = 0.1;
    private static final double PROXIMITY_CAP = 4.0;
    private static final double PROXIMITY_FLOOR = -3.0;
    private static final int MAX_CANDIDATES = 500;
    private static final double DAYS_PER_MONTH = 365.25 / 12;

    // A little randomness so a refresh doesn't always show the exact same lineup - a big enough
    // score gap always wins anyway, only near-ties get shuffled.
    private static final double JITTER_STDDEV = 1.2;
    private static final double WINNER_TIE_EPSILON = 1.0;
    private static final int MAX_WINNERS = 2;
    private static final int MIN_MORE_RESULTS = 3;
    private static final int MAX_MORE_RESULTS = 7;

    private static final String Q_VISITS =
            """
            SELECT d.dive_site AS site_id, max(ds.dive_start) AS last_dive_at, count(*) AS visits
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            GROUP BY d.dive_site
            """;

    private static final String Q_USER_PROFILE =
            """
            SELECT
                (SELECT max(ds.max_depth) FROM t_dives d
                   JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
                   WHERE d.fk_diver_id = :userId) AS max_depth_ever,
                (SELECT count(*) FROM t_certification WHERE fk_user_id = :userId) AS cert_count
            """;

    private static final String Q_CANDIDATES =
            """
            SELECT
                s.pk_dive_site_id AS id, s.name, ST_Y(s.location) AS lat, ST_X(s.location) AS lon,
                s.description, s.country_region, s.max_depth AS declared_max_depth,
                s.site_type, s.water_type,
                st.total_dives, st.distinct_divers, st.recent_dives_30d,
                st.recent_distinct_divers_30d, st.avg_visibility_m, st.visibility_sample_size,
                st.avg_max_depth, st.min_max_depth, st.max_max_depth, st.highlighted_dives,
                (SELECT avg(st2.avg_visibility_m)
                   FROM t_dive_site_stats st2
                   JOIN t_dive_site s2 ON s2.pk_dive_site_id = st2.fk_dive_site_id
                   WHERE st2.fk_dive_site_id <> s.pk_dive_site_id
                     AND st2.avg_visibility_m IS NOT NULL
                     AND ST_DWithin(s2.location::geography, s.location::geography, :neighborhoodMeters)
                ) AS neighborhood_avg_visibility_m,
                CASE WHEN :hasLocation THEN
                    ST_Distance(s.location::geography,
                        ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) / 1000.0
                END AS distance_km
            FROM t_dive_site s
            JOIN t_dive_site_stats st ON st.fk_dive_site_id = s.pk_dive_site_id
            LIMIT :maxCandidates
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Random random;

    @Autowired
    public DiveSiteSuggestionDataService(final NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new Random());
    }

    DiveSiteSuggestionDataService(final NamedParameterJdbcTemplate jdbc, final Random random) {
        this.jdbc = jdbc;
        this.random = random;
    }

    @Transactional(readOnly = true)
    public List<DiveSiteSuggestion> suggest(
            final long userId,
            final @Nullable Double lat,
            final @Nullable Double lon,
            final @Nullable Double maxDistanceKm,
            final int limit) {
        final var userParams = new MapSqlParameterSource("userId", userId);
        final var visits = new HashMap<Long, VisitInfo>();
        jdbc.query(
                Q_VISITS,
                userParams,
                (final ResultSet rs) -> {
                    visits.put(
                            rs.getLong("site_id"),
                            new VisitInfo(ts(rs, "last_dive_at"), rs.getInt("visits")));
                });

        final var profile =
                jdbc.queryForObject(
                        Q_USER_PROFILE,
                        userParams,
                        (rs, i) ->
                                new UserProfile(
                                        dbl(rs, "max_depth_ever"), rs.getInt("cert_count")));

        final var candidateParams =
                new MapSqlParameterSource()
                        .addValue("neighborhoodMeters", NEIGHBORHOOD_METERS)
                        .addValue("hasLocation", lat != null && lon != null)
                        .addValue("lat", lat != null ? lat : 0.0)
                        .addValue("lon", lon != null ? lon : 0.0)
                        .addValue("maxCandidates", MAX_CANDIDATES);
        final var candidates =
                jdbc.query(
                        Q_CANDIDATES, candidateParams, DiveSiteSuggestionDataService::mapCandidate);

        final boolean experienced =
                profile.certCount() >= EXPERIENCED_CERT_COUNT
                        || (profile.maxDepthEver() != null
                                && profile.maxDepthEver() >= EXPERIENCED_DEPTH_M);
        final double effectiveMaxDistanceKm =
                maxDistanceKm != null ? maxDistanceKm : DEFAULT_MAX_DISTANCE_KM;
        final var now = Instant.now();

        final var scored = new ArrayList<Scored>();
        for (final var c : candidates) {
            final var visit = visits.get(c.id());
            Integer daysSinceLastVisit = null;
            int priorVisits = 0;
            if (visit != null && visit.lastDiveAt() != null) {
                daysSinceLastVisit = (int) ChronoUnit.DAYS.between(visit.lastDiveAt(), now);
                if (daysSinceLastVisit < RECENT_VISIT_EXCLUSION_DAYS) {
                    continue;
                }
                priorVisits = visit.visits();
            }

            double score = 0;
            final var reasons = new ArrayList<String>();

            if (daysSinceLastVisit != null) {
                score += Math.min(REVISIT_CAP, daysSinceLastVisit / REVISIT_DAYS_PER_POINT);
                reasons.add(
                        "You dived here "
                                + friendlyDuration(daysSinceLastVisit)
                                + " ago"
                                + (priorVisits > 1 ? " (" + priorVisits + " times before)" : "")
                                + " - due for a revisit.");
            }

            if (c.avgVisibilityM() != null && c.neighborhoodAvgVisibilityM() != null) {
                final double delta = c.avgVisibilityM() - c.neighborhoodAvgVisibilityM();
                score +=
                        clamp(
                                delta * VISIBILITY_EDGE_PER_METER,
                                VISIBILITY_EDGE_FLOOR,
                                VISIBILITY_EDGE_CAP);
                if (delta >= VISIBILITY_EDGE_MENTION_DELTA_M) {
                    reasons.add(
                            String.format(
                                    "Visibility here averages ~%.0fm, better than the ~%.0fm average"
                                            + " nearby.",
                                    c.avgVisibilityM(), c.neighborhoodAvgVisibilityM()));
                }
            }

            if (c.recentDistinctDivers30d() > 0) {
                score += Math.min(POPULARITY_CAP, Math.log(1 + c.recentDistinctDivers30d()) * 2);
                if (c.recentDistinctDivers30d() >= 2) {
                    reasons.add(
                            c.recentDistinctDivers30d()
                                    + " other divers logged dives here in the last 30 days.");
                }
            }

            if (c.totalDives() <= UNDERRATED_MAX_TOTAL_DIVES) {
                final double highlightRate =
                        c.totalDives() > 0 ? (double) c.highlightedDives() / c.totalDives() : 0;
                final boolean goodViz =
                        c.avgVisibilityM() != null
                                && c.avgVisibilityM() >= UNDERRATED_MIN_VISIBILITY_M;
                if (highlightRate >= UNDERRATED_MIN_HIGHLIGHT_RATE || goodViz) {
                    score += UNDERRATED_BONUS;
                    reasons.add(
                            "Only "
                                    + c.totalDives()
                                    + (c.totalDives() == 1 ? " dive" : " dives")
                                    + " logged here so far, but conditions have been rated well -"
                                    + " an underrated pick.");
                }
            }

            if (profile.maxDepthEver() != null && c.maxMaxDepth() != null) {
                final double siteMax = c.maxMaxDepth();
                final double siteMin = c.minMaxDepth() != null ? c.minMaxDepth() : siteMax;
                final boolean wideRange = (siteMax - siteMin) >= DEPTH_WIDE_RANGE_M;
                final double comfortFactor =
                        experienced ? DEPTH_EXPERIENCED_FACTOR : DEPTH_NOVICE_FACTOR;
                if (wideRange || siteMax <= profile.maxDepthEver() * DEPTH_COMFORTABLE_FACTOR) {
                    score += DEPTH_MATCH_BONUS;
                    if (wideRange) {
                        reasons.add(
                                String.format(
                                        "Dives here have ranged from ~%.0fm to ~%.0fm - suits most"
                                                + " experience levels.",
                                        siteMin, siteMax));
                    }
                } else if (siteMax > profile.maxDepthEver() * (comfortFactor + 0.75)
                        && siteMin > profile.maxDepthEver()) {
                    score += DEPTH_MISMATCH_PENALTY;
                    reasons.add(
                            String.format(
                                    "Dives here have gone to ~%.0fm, notably deeper than your own"
                                            + " logged max of ~%.0fm.",
                                    siteMax, profile.maxDepthEver()));
                } else {
                    // Neither comfortable nor clearly mismatched (e.g. a novice's own max is
                    // 20m and this site's dives go to 28m) - still worth a small caution nudge
                    // rather than silently contributing nothing.
                    score += DEPTH_MILD_CAUTION_PENALTY;
                }
            }

            if (c.distanceKm() != null) {
                final double distance = c.distanceKm();
                score +=
                        clamp(
                                PROXIMITY_CAP
                                        - Math.max(0, distance - effectiveMaxDistanceKm)
                                                * PROXIMITY_DECAY_PER_KM,
                                PROXIMITY_FLOOR,
                                PROXIMITY_CAP);
                if (distance <= effectiveMaxDistanceKm) {
                    reasons.add(
                            String.format("Only ~%.0fkm from your current location.", distance));
                }
            }

            if (reasons.isEmpty()) {
                continue;
            }
            scored.add(new Scored(c, score, reasons, daysSinceLastVisit));
        }

        if (scored.isEmpty()) {
            return List.of();
        }

        // Jitter is computed once per candidate up front - a Comparator key extractor may be
        // invoked more than once per element during a sort, and re-rolling it there would both
        // violate the comparator contract and make the ranking non-reproducible mid-sort.
        final var ranked =
                scored.stream()
                        .map(
                                s ->
                                        new RankedScored(
                                                s,
                                                s.score() + random.nextGaussian() * JITTER_STDDEV))
                        .sorted(
                                Comparator.comparingDouble(RankedScored::rankScore)
                                        .reversed()
                                        .thenComparingLong(r -> r.scored().candidate().id()))
                        .map(RankedScored::scored)
                        .toList();

        final var winners = new ArrayList<Scored>();
        winners.add(ranked.getFirst());
        if (ranked.size() > 1
                && Math.abs(ranked.get(0).score() - ranked.get(1).score()) <= WINNER_TIE_EPSILON) {
            winners.add(ranked.get(1));
        }

        final var remainingPool = ranked.subList(winners.size(), ranked.size());
        final int cap =
                Math.min(
                        remainingPool.size(),
                        Math.min(MAX_MORE_RESULTS, Math.max(0, limit - winners.size())));
        final int moreCount =
                cap <= MIN_MORE_RESULTS
                        ? cap
                        : MIN_MORE_RESULTS + random.nextInt(cap - MIN_MORE_RESULTS + 1);

        final var result = new ArrayList<DiveSiteSuggestion>();
        winners.forEach(w -> result.add(toSuggestion(w, true)));
        remainingPool.subList(0, moreCount).forEach(s -> result.add(toSuggestion(s, false)));
        return result;
    }

    private static DiveSiteSuggestion toSuggestion(final Scored s, final boolean topPick) {
        final var c = s.candidate();
        final var site =
                new DiveSite(
                        c.id(),
                        c.name(),
                        c.lat(),
                        c.lon(),
                        c.description(),
                        c.countryRegion(),
                        c.declaredMaxDepth(),
                        c.siteType() != null ? DiveSiteType.valueOf(c.siteType()) : null,
                        c.waterType() != null ? WaterType.valueOf(c.waterType()) : null,
                        List.of(),
                        false);
        return new DiveSiteSuggestion(
                site,
                Math.round(s.score() * 10) / 10.0,
                s.reasons(),
                s.daysSinceLastVisit(),
                c.avgVisibilityM(),
                c.recentDistinctDivers30d(),
                c.totalDives(),
                c.distanceKm(),
                topPick);
    }

    private static String friendlyDuration(final int days) {
        return days < 100
                ? Math.round(days / 7.0) + " weeks"
                : Math.round(days / DAYS_PER_MONTH) + " months";
    }

    private static double clamp(final double v, final double min, final double max) {
        return Math.max(min, Math.min(max, v));
    }

    private record VisitInfo(@Nullable Instant lastDiveAt, int visits) {}

    private record UserProfile(@Nullable Double maxDepthEver, int certCount) {}

    private record Candidate(
            long id,
            String name,
            double lat,
            double lon,
            @Nullable String description,
            @Nullable String countryRegion,
            @Nullable Double declaredMaxDepth,
            @Nullable String siteType,
            @Nullable String waterType,
            int totalDives,
            int distinctDivers,
            int recentDives30d,
            int recentDistinctDivers30d,
            @Nullable Double avgVisibilityM,
            int visibilitySampleSize,
            @Nullable Double avgMaxDepth,
            @Nullable Double minMaxDepth,
            @Nullable Double maxMaxDepth,
            int highlightedDives,
            @Nullable Double neighborhoodAvgVisibilityM,
            @Nullable Double distanceKm) {}

    private record Scored(
            Candidate candidate,
            double score,
            List<String> reasons,
            @Nullable Integer daysSinceLastVisit) {}

    private record RankedScored(Scored scored, double rankScore) {}

    private static Candidate mapCandidate(final ResultSet rs, final int rowNum)
            throws SQLException {
        return new Candidate(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("lat"),
                rs.getDouble("lon"),
                rs.getString("description"),
                rs.getString("country_region"),
                dbl(rs, "declared_max_depth"),
                rs.getString("site_type"),
                rs.getString("water_type"),
                rs.getInt("total_dives"),
                rs.getInt("distinct_divers"),
                rs.getInt("recent_dives_30d"),
                rs.getInt("recent_distinct_divers_30d"),
                dbl(rs, "avg_visibility_m"),
                rs.getInt("visibility_sample_size"),
                dbl(rs, "avg_max_depth"),
                dbl(rs, "min_max_depth"),
                dbl(rs, "max_max_depth"),
                rs.getInt("highlighted_dives"),
                dbl(rs, "neighborhood_avg_visibility_m"),
                dbl(rs, "distance_km"));
    }

    private static @Nullable Instant ts(final ResultSet rs, final String column)
            throws SQLException {
        final Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static @Nullable Double dbl(final ResultSet rs, final String column)
            throws SQLException {
        final var v = rs.getDouble(column);
        return rs.wasNull() ? null : v;
    }
}

package ch.sthomas.stddivelogger.data.service;

import ch.sthomas.stddivelogger.data.repository.DiverReminderRepository;
import ch.sthomas.stddivelogger.data.repository.DiverReminderRunRepository;
import ch.sthomas.stddivelogger.model.dive.home.DiverReminder;
import ch.sthomas.stddivelogger.model.dive.home.NudgeLevel;
import ch.sthomas.stddivelogger.model.dive.home.ReminderKind;
import ch.sthomas.stddivelogger.model.entity.DiverReminderEntity;
import ch.sthomas.stddivelogger.model.entity.DiverReminderRunEntity;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Computes, stores and serves {@link DiverReminder}s - dive anniversaries and the dynamic "time to
 * go diving again" nudge. Raw-SQL aggregates in the {@link HomeDataService} style.
 *
 * <p>The analytics deployable calls {@link #findDiverIdsNeedingRecompute}/{@link #computeAndStore}
 * daily (anniversaries move at midnight); {@code ws} calls {@link #getActiveReminders} (which
 * recomputes lazily if the stored set is from an earlier day) and {@link #dismiss}.
 */
@Service
public class DiverReminderDataService {

    /** How many anniversaries back we bother looking (a sanity cap, not a real limit). */
    private static final int MAX_ANNIVERSARY_YEARS = 40;

    private static final double DAYS_PER_MONTH = 365.25 / 12;

    /** Same composite {@code t_diver_activity_stats} / the recompute query use. */
    private static final String FINGERPRINT_SQL =
            """
            SELECT count(*)::text || '|'
              || coalesce(sum(extract(epoch FROM ds.dive_start))::bigint, 0)::text || '|'
              || coalesce(sum(d.pk_dive_id), 0)::text || '|'
              || coalesce(extract(epoch FROM max(d.updated_at))::bigint, 0)::text || '|'
              || coalesce(count(*) FILTER (WHERE d.highlighted), 0)::text
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            """;

    // One row per "years ago" bucket for dives whose month+day match today. The representative
    // ("best") dive is the highlighted one, else the deepest, else the lowest id.
    // TODO(tz): month/day are compared in the DB session timezone; a dive logged just before
    // local midnight in a far-away timezone could land its anniversary a day off. Fine for a
    // sentimental nudge; revisit if we ever store a per-user timezone.
    private static final String Q_ANNIVERSARIES =
            """
            SELECT
              date_part('year', age(current_date, ds.dive_start::date))::int          AS years_ago,
              count(*)                                                                 AS dives_that_day,
              count(DISTINCT d.dive_site)                                              AS distinct_sites,
              bool_or(d.highlighted)                                                   AS any_highlighted,
              (array_agg(d.pk_dive_id      ORDER BY d.highlighted DESC, ds.max_depth DESC NULLS LAST, d.pk_dive_id))[1] AS best_dive_id,
              (array_agg(s.name            ORDER BY d.highlighted DESC, ds.max_depth DESC NULLS LAST, d.pk_dive_id))[1] AS best_site,
              (array_agg(ds.max_depth      ORDER BY d.highlighted DESC, ds.max_depth DESC NULLS LAST, d.pk_dive_id))[1] AS best_depth,
              (array_agg(ds.duration_seconds ORDER BY d.highlighted DESC, ds.max_depth DESC NULLS LAST, d.pk_dive_id))[1] AS best_seconds
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            JOIN t_dive_site s     ON s.pk_dive_site_id = d.dive_site
            WHERE d.fk_diver_id = :userId
              AND extract(month FROM ds.dive_start) = extract(month FROM current_date)
              AND extract(day   FROM ds.dive_start) = extract(day   FROM current_date)
              AND ds.dive_start < date_trunc('year', current_date)
            GROUP BY 1
            ORDER BY 1
            """;

    private static final String Q_LAST_DIVE_START =
            """
            SELECT max(ds.dive_start)
            FROM t_dives d
            JOIN t_dive_summary ds ON ds.fk_dive_id = d.pk_dive_id
            WHERE d.fk_diver_id = :userId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DiverReminderRepository reminderRepo;
    private final DiverReminderRunRepository runRepo;
    private final DiverActivityStatsDataService activityStats;

    public DiverReminderDataService(
            final NamedParameterJdbcTemplate jdbc,
            final DiverReminderRepository reminderRepo,
            final DiverReminderRunRepository runRepo,
            final DiverActivityStatsDataService activityStats) {
        this.jdbc = jdbc;
        this.reminderRepo = reminderRepo;
        this.runRepo = runRepo;
        this.activityStats = activityStats;
    }

    @Transactional(readOnly = true)
    public List<Long> findDiverIdsNeedingRecompute(final int limit) {
        return runRepo.findDiverIdsNeedingRecompute(limit);
    }

    /**
     * Recompute today's reminder set for one diver and upsert it (keeping dismissal/push state).
     */
    @Transactional
    public void computeAndStore(final long userId) {
        final var today = today();
        final var fingerprint = fingerprint(userId);

        final var desired = new ArrayList<DesiredReminder>();
        anniversaryReminder(userId, today).ifPresent(desired::add);
        nudgeReminder(userId, today).ifPresent(desired::add);

        for (final var d : desired) {
            reminderRepo
                    .findByDiverIdAndDedupeKey(userId, d.dedupeKey())
                    .ifPresentOrElse(
                            e -> e.refresh(d.relevantOn(), d.expiresAt(), d.title(), d.body()),
                            () -> reminderRepo.save(d.toEntity(userId)));
        }

        runRepo.findByDiverId(userId)
                .ifPresentOrElse(
                        r -> r.update(today, fingerprint),
                        () -> runRepo.save(new DiverReminderRunEntity(userId, today, fingerprint)));
    }

    /**
     * A diver's live, not-dismissed reminders - recomputing first if the stored set is from an
     * earlier day (the analytics job normally keeps it current; this covers a diver who loads the
     * page before the job gets to them). Writes, so the caller's transaction must not be read-only.
     */
    @Transactional
    public List<DiverReminder> getActiveReminders(final long userId) {
        final boolean stale =
                runRepo.findByDiverId(userId)
                        .map(r -> !today().equals(r.getComputedOn()))
                        .orElse(true);
        if (stale) {
            computeAndStore(userId);
        }
        return reminderRepo
                .findByDiverIdAndDismissedAtIsNullAndExpiresAtAfterOrderByRelevantOnDescCreatedAtDesc(
                        userId, Instant.now())
                .stream()
                .map(DiverReminderEntity::toReminder)
                .toList();
    }

    /**
     * @return true if the reminder existed and belonged to this user.
     */
    @Transactional
    public boolean dismiss(final long userId, final long reminderId) {
        return reminderRepo
                .findById(reminderId)
                .filter(r -> r.getDiverId() == userId)
                .map(
                        r -> {
                            r.dismiss();
                            return true;
                        })
                .orElse(false);
    }

    /** Reminders that are ready to be web-pushed (the send itself is TODO - see WebPushSender). */
    @Transactional(readOnly = true)
    public List<PushableReminder> findDuePushes() {
        return reminderRepo.findDuePushes(today(), Instant.now()).stream()
                .map(
                        r ->
                                new PushableReminder(
                                        r.getId(),
                                        r.getDiverId(),
                                        r.getKind(),
                                        r.getTitle(),
                                        r.getBody(),
                                        r.getDiveId()))
                .toList();
    }

    @Transactional
    public void markPushed(final long reminderId) {
        reminderRepo.findById(reminderId).ifPresent(DiverReminderEntity::markPushed);
    }

    /** Nightly cleanup: drop reminders that are well past their {@code expires_at}. */
    @Transactional
    public int purgeExpired() {
        return reminderRepo.deleteByExpiresAtBefore(Instant.now().minus(Duration.ofDays(2)));
    }

    /** Lightweight view of a reminder that needs pushing (id + who + payload text). */
    public record PushableReminder(
            long reminderId,
            long userId,
            ReminderKind kind,
            String title,
            String body,
            @Nullable Long diveId) {}

    // ---------------------------------------------------------------------------------------------

    private Optional<DesiredReminder> anniversaryReminder(
            final long userId, final LocalDate today) {
        final var rows =
                jdbc.query(
                        Q_ANNIVERSARIES,
                        new MapSqlParameterSource("userId", userId),
                        DiverReminderDataService::anniversaryRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        // Pick the standout year: a highlighted dive wins, then a round anniversary (5/10/...),
        // then simply the longest ago. One anniversary banner a day - a full "on this day" list
        // is a separate feature.
        final var pick =
                rows.stream()
                        .filter(r -> r.yearsAgo() >= 1 && r.yearsAgo() <= MAX_ANNIVERSARY_YEARS)
                        .max(
                                java.util.Comparator.comparingInt(
                                        (AnniversaryRow r) ->
                                                (r.anyHighlighted() ? 100 : 0)
                                                        + (r.yearsAgo() % 5 == 0 ? 30 : 0)
                                                        + r.yearsAgo()))
                        .orElse(null);
        if (pick == null) {
            return Optional.empty();
        }

        final String title =
                pick.yearsAgo() == 1 ? "One year ago today" : pick.yearsAgo() + " years ago today";
        final String body = anniversaryBody(pick);
        // One slot per day; refreshed in place if the pick changes (e.g. a new import).
        final String key = "anniv:" + today;
        final Instant expires =
                today.plusDays(1).atStartOfDay(ZoneOffset.UTC).plusHours(12).toInstant();
        return Optional.of(
                new DesiredReminder(
                        ReminderKind.DIVE_ANNIVERSARY,
                        key,
                        today,
                        expires,
                        title,
                        body,
                        pick.bestDiveId(),
                        pick.yearsAgo(),
                        true));
    }

    private static String anniversaryBody(final AnniversaryRow r) {
        if (r.divesThatDay() > 1) {
            return r.distinctSites() > 1
                    ? r.divesThatDay() + " dives across " + r.distinctSites() + " sites"
                    : r.divesThatDay() + " dives at " + nullToDive(r.bestSite());
        }
        final var parts = new ArrayList<String>();
        parts.add(nullToDive(r.bestSite()));
        if (r.bestDepth() != null) {
            parts.add(Math.round(r.bestDepth()) + " m");
        }
        if (r.bestSeconds() != null && r.bestSeconds() > 0) {
            parts.add(Math.round(r.bestSeconds() / 60.0) + " min");
        }
        return String.join(" · ", parts);
    }

    private static String nullToDive(final @Nullable String site) {
        return site == null || site.isBlank() ? "a dive" : site;
    }

    private Optional<DesiredReminder> nudgeReminder(final long userId, final LocalDate today) {
        final var stats = activityStats.getOrCompute(userId);
        final var level = stats.nudgeLevel();
        final Integer daysSince = stats.daysSinceLastDive();
        if (level == NudgeLevel.NONE || daysSince == null) {
            return Optional.empty();
        }
        final Instant lastStart = lastDiveStart(userId);
        if (lastStart == null) {
            return Optional.empty();
        }
        // Keyed on the last dive (so it's a new reminder once they dive again) and the level (so
        // an escalation GENTLE -> KEEN re-surfaces even a dismissed one, once).
        final long lastEpochDay = lastStart.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
        final String key = "nudge:" + lastEpochDay + ":" + level;

        final String been = staleFragment(daysSince);
        final Integer cadence = stats.recentCadenceDays();
        final String every =
                cadence == null
                        ? ""
                        : cadence >= 12
                                ? " — you usually dive about every "
                                        + Math.round(cadence / 7.0)
                                        + " weeks"
                                : " — you usually dive about every " + cadence + " days";

        final String title;
        final String body;
        final boolean pushable;
        switch (level) {
            case GENTLE -> {
                title = "Been a little while";
                body = "It's been " + been + " since your last dive" + every + ".";
                pushable = true;
            }
            case KEEN -> {
                title = "Time to go diving again";
                body =
                        "It's been "
                                + been
                                + " since your last dive"
                                + every
                                + ". Plan the next one?";
                pushable = true;
            }
            case DORMANT -> {
                title = "Still thinking about diving?";
                body =
                        "It's been "
                                + been
                                + " since your last dive. Your logbook's here whenever you're"
                                + " back.";
                pushable = false; // don't push someone who's clearly on a long break
            }
            default -> {
                return Optional.empty();
            }
        }
        final Instant expires = today.plusDays(10).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Optional.of(
                new DesiredReminder(
                        ReminderKind.DIVE_AGAIN_NUDGE,
                        key,
                        today,
                        expires,
                        title,
                        body,
                        null,
                        null,
                        pushable));
    }

    /** "3 weeks" under ~100 days, "5 months" beyond - matches the frontend's staleFragment(). */
    static String staleFragment(final int days) {
        return days < 100
                ? Math.round(days / 7.0) + " weeks"
                : Math.round(days / DAYS_PER_MONTH) + " months";
    }

    private @Nullable Instant lastDiveStart(final long userId) {
        final var ts =
                jdbc.queryForObject(
                        Q_LAST_DIVE_START,
                        new MapSqlParameterSource("userId", userId),
                        java.sql.Timestamp.class);
        return ts == null ? null : ts.toInstant();
    }

    private LocalDate today() {
        return Objects.requireNonNull(
                        jdbc.getJdbcTemplate()
                                .queryForObject("SELECT current_date", java.sql.Date.class))
                .toLocalDate();
    }

    private String fingerprint(final long userId) {
        return Objects.requireNonNull(
                jdbc.queryForObject(
                        FINGERPRINT_SQL,
                        new MapSqlParameterSource("userId", userId),
                        String.class));
    }

    // ---------------------------------------------------------------------------------------------

    private record DesiredReminder(
            ReminderKind kind,
            String dedupeKey,
            LocalDate relevantOn,
            Instant expiresAt,
            String title,
            String body,
            @Nullable Long diveId,
            @Nullable Integer yearsAgo,
            boolean pushable) {

        DiverReminderEntity toEntity(final long userId) {
            final var e =
                    new DiverReminderEntity(
                            userId,
                            kind,
                            dedupeKey,
                            relevantOn,
                            expiresAt,
                            title,
                            body,
                            diveId,
                            yearsAgo);
            if (!pushable) {
                e.suppressPush();
            }
            return e;
        }
    }

    private record AnniversaryRow(
            int yearsAgo,
            int divesThatDay,
            int distinctSites,
            boolean anyHighlighted,
            @Nullable Long bestDiveId,
            @Nullable String bestSite,
            @Nullable Double bestDepth,
            @Nullable Long bestSeconds) {}

    private static AnniversaryRow anniversaryRow(final ResultSet rs, final int rowNum)
            throws SQLException {
        return new AnniversaryRow(
                rs.getInt("years_ago"),
                rs.getInt("dives_that_day"),
                rs.getInt("distinct_sites"),
                rs.getBoolean("any_highlighted"),
                nullableLong(rs, "best_dive_id"),
                rs.getString("best_site"),
                nullableDouble(rs, "best_depth"),
                nullableLong(rs, "best_seconds"));
    }

    private static @Nullable Long nullableLong(final ResultSet rs, final String col)
            throws SQLException {
        final var v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static @Nullable Double nullableDouble(final ResultSet rs, final String col)
            throws SQLException {
        final var v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}

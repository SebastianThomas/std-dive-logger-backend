package ch.sthomas.stddivelogger.data.service;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class AnalyticsJobRunStore {
    private final JdbcTemplate jdbc;

    public AnalyticsJobRunStore(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean enqueue(final String job, final String trigger) {
        return jdbc.update(
                        "INSERT INTO t_analytics_job_run(job, trigger, status) VALUES (?, ?, 'QUEUED') ON CONFLICT DO NOTHING",
                        job,
                        trigger)
                == 1;
    }

    public List<Run> recent() {
        return jdbc.query(
                "SELECT * FROM t_analytics_job_run ORDER BY id DESC LIMIT 100",
                (rs, row) ->
                        new Run(
                                rs.getLong("id"),
                                rs.getString("job"),
                                rs.getString("trigger"),
                                rs.getString("status"),
                                rs.getTimestamp("queued_at").toInstant(),
                                instant(rs.getTimestamp("started_at")),
                                instant(rs.getTimestamp("finished_at")),
                                rs.getString("error_type")));
    }

    public List<Run> active() {
        return recent().stream()
                .filter(r -> r.status().equals("QUEUED") || r.status().equals("RUNNING"))
                .toList();
    }

    public @Nullable Run claimNext() {
        // Called only while the worker owns the database-wide advisory lock.
        jdbc.update(
                "UPDATE t_analytics_job_run SET status='INTERRUPTED', finished_at=now() WHERE status='RUNNING'");
        final var ids =
                jdbc.queryForList(
                        "UPDATE t_analytics_job_run SET status='RUNNING', started_at=now() WHERE id=(SELECT id FROM t_analytics_job_run WHERE status='QUEUED' ORDER BY id LIMIT 1) RETURNING id",
                        Long.class);
        if (ids.isEmpty()) return null;
        return recent().stream().filter(r -> r.id() == ids.getFirst()).findFirst().orElseThrow();
    }

    public void finish(final long id, final @Nullable String errorType) {
        jdbc.update(
                "UPDATE t_analytics_job_run SET status=?, finished_at=now(), error_type=? WHERE id=? AND status='RUNNING'",
                errorType == null ? "SUCCEEDED" : "FAILED",
                errorType,
                id);
        jdbc.update(
                "DELETE FROM t_analytics_job_run WHERE finished_at < now() - interval '30 days'");
    }

    private static @Nullable Instant instant(final @Nullable Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record Run(
            long id,
            String job,
            String trigger,
            String status,
            Instant queuedAt,
            @Nullable Instant startedAt,
            @Nullable Instant finishedAt,
            @Nullable String errorType) {}
}

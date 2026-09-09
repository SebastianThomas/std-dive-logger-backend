package ch.sthomas.stddivelogger.data.service;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Durable state for asynchronous osm2pgsql Kubernetes imports. */
@Service
public class MapsImportRunStore {

    private final NamedParameterJdbcTemplate mapsJdbc;

    public MapsImportRunStore(
            @Qualifier("mapsNamedParameterJdbcTemplate")
                    final NamedParameterJdbcTemplate mapsJdbc) {
        this.mapsJdbc = mapsJdbc;
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public long plan(
            final String sourceUrl,
            final @Nullable String checksum,
            final Instant sourceTimestamp,
            final String jobName,
            final String state) {
        final var params =
                new MapSqlParameterSource()
                        .addValue("sourceUrl", sourceUrl)
                        .addValue("checksum", checksum)
                        .addValue("sourceTimestamp", sourceTimestamp)
                        .addValue("jobName", jobName)
                        .addValue("state", state);
        final Long id =
                mapsJdbc.queryForObject(
                        """
                        INSERT INTO maps.import_run (
                            source_url, source_checksum, source_timestamp,
                            kubernetes_job_name, status, state
                        ) VALUES (
                            :sourceUrl, :checksum, :sourceTimestamp, :jobName, 'PLANNED', :state
                        )
                        RETURNING pk_import_run_id
                        """,
                        params,
                        Long.class);
        if (id == null) throw new IllegalStateException("Maps import run was not created");
        return id;
    }

    @Transactional(transactionManager = "mapsTransactionManager", readOnly = true)
    public @Nullable String latestSuccessfulState() {
        return mapsJdbc
                .query(
                        """
                        SELECT state
                        FROM maps.import_run
                        WHERE status = 'SUCCEEDED'
                        ORDER BY finished_at DESC, pk_import_run_id DESC
                        LIMIT 1
                        """,
                        (resultSet, rowNumber) -> resultSet.getString("state"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public void markSubmitted(final long runId) {
        updateStatus(runId, "SUBMITTED", null);
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public void markRunning(final long runId) {
        updateStatus(runId, "RUNNING", null);
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public void markFailed(final long runId, final String failureSummary) {
        updateStatus(runId, "FAILED", failureSummary);
    }

    private void updateStatus(
            final long runId, final String status, final @Nullable String failureSummary) {
        mapsJdbc.update(
                """
                UPDATE maps.import_run
                SET status = :status,
                    failure_summary = :failureSummary,
                    finished_at = CASE WHEN :status = 'FAILED' THEN now() ELSE finished_at END
                WHERE pk_import_run_id = :runId
                """,
                new MapSqlParameterSource("runId", runId)
                        .addValue("status", status)
                        .addValue("failureSummary", failureSummary));
    }
}

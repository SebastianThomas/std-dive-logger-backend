package ch.sthomas.stddivelogger.data.service;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/** Durable state for the asynchronous boundary import Kubernetes Jobs. */
@Service
public class MapsImportRunStore {

    /** Statement separator used by the promotion scripts of {@link MapsImportKind}. */
    private static final String STATEMENT_SEPARATOR = "\n--;;\n";

    private final NamedParameterJdbcTemplate mapsJdbc;

    public MapsImportRunStore(
            @Qualifier("mapsNamedParameterJdbcTemplate")
                    final NamedParameterJdbcTemplate mapsJdbc) {
        this.mapsJdbc = mapsJdbc;
    }

    @Transactional(transactionManager = "mapsTransactionManager")
    public long plan(
            final MapsImportKind kind,
            final String sourceUrl,
            final @Nullable String checksum,
            final Instant sourceTimestamp,
            final String jobName,
            final String state) {
        final var params =
                new MapSqlParameterSource()
                        .addValue("sourceUrl", sourceUrl)
                        .addValue("checksum", checksum)
                        .addValue("sourceTimestamp", sourceTimestamp.atOffset(ZoneOffset.UTC))
                        .addValue("jobName", jobName)
                        .addValue("state", state)
                        .addValue("kind", kind.name());
        final Long id =
                mapsJdbc.queryForObject(
                        """
                        INSERT INTO maps.import_run (
                            source_url, source_checksum, source_timestamp,
                            kubernetes_job_name, status, state, kind
                        ) VALUES (
                            :sourceUrl, :checksum, :sourceTimestamp, :jobName, 'PLANNED', :state,
                            :kind
                        )
                        RETURNING pk_import_run_id
                        """,
                        params,
                        Long.class);
        if (id == null) throw new IllegalStateException("Maps import run was not created");
        return id;
    }

    @Transactional(transactionManager = "mapsTransactionManager", readOnly = true)
    public @Nullable String latestSuccessfulState(final MapsImportKind kind) {
        return mapsJdbc
                .query(
                        """
                        SELECT state
                        FROM maps.import_run
                        WHERE status = 'SUCCEEDED'
                          AND kind = :kind
                        ORDER BY finished_at DESC, pk_import_run_id DESC
                        LIMIT 1
                        """,
                        new MapSqlParameterSource("kind", kind.name()),
                        (resultSet, rowNumber) -> resultSet.getString("state"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Promotes the staging tables of the run's source into the live maps schema and marks the run
     * as succeeded, all in one transaction.
     *
     * @return whether this call promoted the run; {@code false} when it is no longer promotable,
     *     which is the normal outcome for a completed Job that was already promoted.
     */
    @Transactional(transactionManager = "mapsTransactionManager")
    public boolean promote(final long runId, final MapsImportKind kind) {
        final var params = new MapSqlParameterSource("importRunId", runId);
        // Claims the run under a row lock so a repeated reconciliation cannot promote it twice.
        final int claimed =
                mapsJdbc.update(
                        """
                        UPDATE maps.import_run
                        SET status = 'RUNNING'
                        WHERE pk_import_run_id = :importRunId
                          AND status IN ('PLANNED', 'SUBMITTED', 'RUNNING')
                        """,
                        params);
        if (claimed == 0) return false;
        promotionStatements(kind).forEach(statement -> mapsJdbc.update(statement, params));
        return true;
    }

    private static List<String> promotionStatements(final MapsImportKind kind) {
        final String script;
        try (var input = new ClassPathResource(kind.getPromotionScript()).getInputStream()) {
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read the boundary promotion script", exception);
        }
        return Arrays.stream(script.split(STATEMENT_SEPARATOR)).map(String::trim).toList();
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

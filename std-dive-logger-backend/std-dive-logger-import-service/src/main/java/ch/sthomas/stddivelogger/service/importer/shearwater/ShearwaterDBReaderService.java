package ch.sthomas.stddivelogger.service.importer.shearwater;

import ch.sthomas.stddivelogger.data.importer.sqlitedb.DiveRowMappers;
import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.importer.SqliteDBFile;
import ch.sthomas.stddivelogger.model.user.User;

import org.apache.commons.lang3.NotImplementedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

@Service
public class ShearwaterDBReaderService {
    public Dive importDB(
            final User user,
            final String filename,
            final UploadDiveBody body,
            final InputStream inputStream)
            throws IOException {
        final var tempDB = Files.createTempFile("shearwater", ".db");
        try (inputStream) {
            Files.copy(inputStream, tempDB, StandardCopyOption.REPLACE_EXISTING);
        }
        try (final var connection =
                        DriverManager.getConnection("jdbc:sqlite:" + tempDB.toAbsolutePath());
                final var dataSource = new SingleConnectionDataSource(connection, false)) {
            final var jdbcTemplate = new JdbcTemplate(dataSource);

            return importDiveDB(jdbcTemplate);
        } catch (final SQLException e) {
            throw new IOException(e);
        }
    }

    Dive importDiveDB(final JdbcTemplate jdbcTemplate) {
        final var computers = getStoredComputers(jdbcTemplate);
        final var diveDetails = getAllDiveDetails(jdbcTemplate);
        // final var logs = getAllDiveLogs(jdbcTemplate);
        // final var records =
        // logs.stream().map(log -> getDiveLogRecords(jdbcTemplate, String.valueOf(log.id())));
        final var sites = getAllDiveSites(jdbcTemplate);

        throw new NotImplementedException();
    }

    private List<SqliteDBFile.DiveDetails> getAllDiveDetails(final JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM dive_details", DiveRowMappers.DIVE_DETAILS_MAPPER);
    }

    private List<SqliteDBFile.DiveLogRecord> getDiveLogRecords(
            final JdbcTemplate jdbcTemplate, final String diveLogId) {
        return jdbcTemplate.query(
                "SELECT * FROM dive_log_records WHERE diveLogId = ?",
                DiveRowMappers.DIVE_LOG_RECORDS_MAPPER,
                diveLogId);
    }

    //    private List<SqliteDBFile.DiveLog> getAllDiveLogs(final JdbcTemplate jdbcTemplate) {
    //        return jdbcTemplate.query("SELECT * FROM dive_logs", DiveRowMappers.DIVE_LOG_MAPPER);
    //    }

    private List<SqliteDBFile.DiveSite> getAllDiveSites(final JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query("SELECT * FROM dive_site", DiveRowMappers.DIVE_SITE_MAPPER);
    }

    private List<SqliteDBFile.StoredDiveComputer> getStoredComputers(
            final JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.query(
                "SELECT * FROM StoredDiveComputer", DiveRowMappers.STORED_DIVE_COMPUTER_MAPPER);
    }
}

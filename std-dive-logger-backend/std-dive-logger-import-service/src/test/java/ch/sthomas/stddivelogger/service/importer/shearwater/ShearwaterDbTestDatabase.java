package ch.sthomas.stddivelogger.service.importer.shearwater;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

/**
 * Writes a synthetic Shearwater Cloud database containing only the two tables the reader actually
 * uses, with the app's real column names - enough to drive the import end to end without the
 * (personal, gitignored) real logbook fixture. Shared with {@code import-ws}'s integration test via
 * this module's test-jar.
 */
public final class ShearwaterDbTestDatabase {
    private ShearwaterDbTestDatabase() {}

    /** One {@code dive_details} row plus its {@code log_data} blob. */
    public record Dive(
            String diveId,
            @Nullable String diveNumber,
            @Nullable String location,
            @Nullable String buddy,
            @Nullable String notes,
            @Nullable String visibility,
            @Nullable String weight,
            @Nullable String tankSize,
            @Nullable String apparatus,
            @Nullable String tankProfileData,
            byte[] logBlob) {

        public Dive withDiveNumber(final String newDiveNumber) {
            return new Dive(
                    diveId,
                    newDiveNumber,
                    location,
                    buddy,
                    notes,
                    visibility,
                    weight,
                    tankSize,
                    apparatus,
                    tankProfileData,
                    logBlob);
        }
    }

    public static void write(final Path file, final List<Dive> dives) throws SQLException {
        try (final var connection =
                DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            try (final var statement = connection.createStatement()) {
                statement.executeUpdate(
                        """
                        CREATE TABLE dive_details ("DiveId" varchar primary key not null,
                          "FileName" varchar, "DiveDate" datetime, "SerialNumber" varchar,
                          "DiveNumber" varchar, "Location" varchar, "Site" varchar,
                          "Buddy" varchar, "Notes" varchar, "Visibility" varchar,
                          "Environment" varchar, "Weight" varchar, "TankSize" varchar,
                          "Apparatus" varchar, "TankProfileData" varchar)
                        """);
                statement.executeUpdate(
                        """
                        CREATE TABLE log_data ("log_id" varchar primary key not null,
                          "data_bytes_1" blob)
                        """);
            }
            for (final var dive : dives) {
                insert(connection, dive);
            }
        }
    }

    private static void insert(final java.sql.Connection connection, final Dive dive)
            throws SQLException {
        try (final var insert =
                connection.prepareStatement(
                        "INSERT INTO dive_details VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, dive.diveId());
            insert.setString(2, "Perdix 2[A3B6F031]#" + dive.diveNumber() + ".swlogzp");
            insert.setString(3, "2024-11-29 09:58:01");
            insert.setString(4, "A3B6F031");
            insert.setString(5, dive.diveNumber());
            insert.setString(6, dive.location());
            insert.setString(7, null);
            insert.setString(8, dive.buddy());
            insert.setString(9, dive.notes());
            insert.setString(10, dive.visibility());
            insert.setString(11, "Lake/Quarry");
            insert.setString(12, dive.weight());
            insert.setString(13, dive.tankSize());
            insert.setString(14, dive.apparatus());
            insert.setString(15, dive.tankProfileData());
            insert.executeUpdate();
        }
        try (final var insert = connection.prepareStatement("INSERT INTO log_data VALUES (?,?)")) {
            insert.setString(1, dive.diveId());
            insert.setBytes(2, dive.logBlob());
            insert.executeUpdate();
        }
    }
}

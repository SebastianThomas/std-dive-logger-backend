package ch.sthomas.stddivelogger.service.importer.garmin.shearwater;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.controller.dive.UploadFileType;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.importer.shearwater.ShearwaterDBReaderService;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

class ShearwaterDBReaderServiceTest {
    @Test
    @Disabled("Requires DB file")
    void testReadDB() throws IOException {
        final var service = new ShearwaterDBReaderService();
        final var filename = "2025-11-29.db";
        final var body = new UploadDiveBody(null, null, null, UploadFileType.DB);
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var dive =
                    service.importDB(
                            new User(1, "", "", "", Instant.now(), Instant.now()),
                            filename,
                            body,
                            inputStream);
            assertNotNull(dive);
        }
    }
}

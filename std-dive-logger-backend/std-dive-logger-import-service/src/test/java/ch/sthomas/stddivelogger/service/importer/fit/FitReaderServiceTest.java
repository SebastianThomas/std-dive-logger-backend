package ch.sthomas.stddivelogger.service.importer.fit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.model.controller.dive.UploadDiveBody;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import com.garmin.fit.DateTime;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;

public class FitReaderServiceTest {
    private static final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(FitReaderServiceTest.class);

    @ParameterizedTest
    @ValueSource(strings = {"35 Malapascua Gato Island Tunnel.fit"})
    @Disabled("Files not checked in, too much private personal information")
    void readFit(final String filename) throws IOException {
        final var service = new FitReaderService(mock(DiveService.class));
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var result =
                    service.readFitAndSaveDive(
                            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                            filename,
                            new UploadDiveBody(1, "TestID", null, null, null),
                            inputStream);
            assertNotNull(result);
        }
    }

    @RepeatedTest(5)
    void testReadTime() {
        final var date =
                new GregorianCalendar(
                        random.nextInt(2000, 2030),
                        random.nextInt(Calendar.JANUARY, Calendar.DECEMBER + 1),
                        random.nextInt(1, 29),
                        random.nextInt(0, 24),
                        random.nextInt(0, 60),
                        random.nextInt(0, 60));
        date.set(Calendar.MILLISECOND, random.nextInt(0, 1000));
        final var garminTime = new DateTime(date.getTime());
        final var expectedInstant = date.toInstant();
        final var actualInstant = FitReaderService.toInstant(garminTime);
        assertEquals(expectedInstant, actualInstant);
        logger.info("Tested date conversion for {}", expectedInstant);
    }
}

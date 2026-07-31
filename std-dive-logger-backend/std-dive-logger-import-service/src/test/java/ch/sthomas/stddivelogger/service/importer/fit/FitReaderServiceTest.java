package ch.sthomas.stddivelogger.service.importer.fit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSummary;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import com.garmin.fit.DateTime;
import com.garmin.fit.DiveGasMesg;
import com.garmin.fit.FitMessages;
import com.garmin.fit.RecordMesg;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class FitReaderServiceTest {
    private static final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(FitReaderServiceTest.class);
    private static final DiveComputer computer =
            new DiveComputer(1L, new DiveComputerManufacturer(1L, "Garmin"), "serial", "serial");
    private final FitReaderService service = new FitReaderService(mock(DiveService.class));

    private static RecordMesg recordAt(final Instant time) {
        final var record = mock(RecordMesg.class);
        when(record.getTimestamp()).thenReturn(new DateTime(Date.from(time)));
        when(record.getTemperature()).thenReturn((byte) 20);
        return record;
    }

    private static Optional<DiveProfileSummary> summaryAt(final Instant start) {
        return Optional.of(
                new DiveProfileSummary(
                        start,
                        start.plusSeconds(60),
                        0,
                        0,
                        null,
                        Duration.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    @Test
    void emptyGasListFallsBackToNullGasInsteadOfThrowing() {
        final var record = recordAt(Instant.now());
        final var profile =
                assertDoesNotThrow(
                        () ->
                                service.getDiveProfile(
                                        List.of(record),
                                        List.of(),
                                        List.of(),
                                        computer,
                                        summaryAt(Instant.now())));
        assertNull(profile.measurements().getFirst().gas());
    }

    @Test
    void missingDepthFieldDefaultsToZeroInsteadOfThrowing() {
        final var record = recordAt(Instant.now());
        when(record.getField(RecordMesg.DepthFieldNum)).thenReturn(null);
        final var profile =
                assertDoesNotThrow(
                        () ->
                                service.getDiveProfile(
                                        List.of(record),
                                        List.of(),
                                        List.of(Gas.AIR),
                                        computer,
                                        summaryAt(Instant.now())));
        assertEquals(0.0, profile.measurements().getFirst().depth());
    }

    @Test
    void missingOxygenAndHeliumContentDefaultToAirInsteadOfThrowing() {
        final var gasMsg = mock(DiveGasMesg.class);
        when(gasMsg.getOxygenContent()).thenReturn(null);
        when(gasMsg.getHeliumContent()).thenReturn(null);
        final var messages = mock(FitMessages.class);
        when(messages.getDiveGasMesgs()).thenReturn(List.of(gasMsg));

        final var gases = assertDoesNotThrow(() -> FitReaderService.getGases(messages));

        assertEquals(1, gases.size());
        assertEquals(0.21, gases.getFirst().o2(), 0.0001);
        assertEquals(0.0, gases.getFirst().he(), 0.0001);
    }

    @ParameterizedTest
    @ValueSource(strings = {"35 Malapascua Gato Island Tunnel.fit"})
    @Disabled("Files not checked in, too much private personal information")
    void readFit(final String filename) throws IOException {
        final var service = new FitReaderService(mock(DiveService.class));
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var result =
                    service.parse(
                            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                            filename,
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

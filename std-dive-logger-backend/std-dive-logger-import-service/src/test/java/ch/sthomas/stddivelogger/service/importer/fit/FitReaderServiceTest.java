package ch.sthomas.stddivelogger.service.importer.fit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSummary;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import com.garmin.fit.DateTime;
import com.garmin.fit.DiveGasMesg;
import com.garmin.fit.DiveSummaryMesg;
import com.garmin.fit.FitMessages;
import com.garmin.fit.RecordMesg;

import org.junit.jupiter.api.Assumptions;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class FitReaderServiceTest {
    private static final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(FitReaderServiceTest.class);
    private static final DiveComputer computer =
            new DiveComputer(
                    1L, new DiveComputerManufacturer(1L, "Garmin"), "serial", "serial", null);
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

    // getSummary()/getDeco() are the two methods with the double-scaling bug fixed here: FIT's
    // typed getters (getAvgDepth(), getBottomTime(), getNextStopDepth(), ...) already apply their
    // field's scale internally (confirmed via javap on com.garmin.fit.DiveSummaryMesg/RecordMesg),
    // so the values below are exactly what a mocked message's typed getter would return - the
    // pre-fix code additionally divided several of them by 1000/100 again. Mocking the message
    // directly (rather than relying on a real fixture file) is what actually exercises and proves
    // the fix, since neither field ends up in a real parsed DiveProfileUpload's per-sample data
    // (see the fixture-based tests below for what those already-correct fields cover instead).
    @Test
    void getSummaryDoesNotDoubleScaleFieldsTheFitSdkAlreadyScales() {
        final var first = mock(DiveSummaryMesg.class);
        when(first.getTimestamp()).thenReturn(new DateTime(Date.from(Instant.EPOCH)));
        final var last = mock(DiveSummaryMesg.class);
        when(last.getTimestamp())
                .thenReturn(new DateTime(Date.from(Instant.EPOCH.plusSeconds(3600))));
        // Real, already-scaled values empirically decoded from "35 Malapascua Gato Island
        // Tunnel.fit"'s own DiveSummaryMesg.
        when(last.getAvgDepth()).thenReturn(9.764f);
        when(last.getMaxDepth()).thenReturn(19.42f);
        when(last.getSurfaceInterval()).thenReturn(0L);
        when(last.getBottomTime()).thenReturn(3489.398f);
        when(last.getDescentTime()).thenReturn(45.0f);
        when(last.getAscentTime()).thenReturn(25.398f);
        when(last.getAvgAscentRate()).thenReturn(0.052f);
        when(last.getStartN2()).thenReturn(10);
        when(last.getEndN2()).thenReturn(20);
        when(last.getO2Toxicity()).thenReturn(3);
        when(last.getStartCns()).thenReturn((short) 3);
        when(last.getEndCns()).thenReturn((short) 4);

        final var summary = service.getSummary(List.of(first, last)).orElseThrow();

        assertEquals(9.764, summary.averageDepth(), 0.001);
        assertEquals(19.42, summary.maxDepth(), 0.001);
        assertEquals(3489398, summary.bottomTime().toMillis(), 1);
        assertEquals(45000, Objects.requireNonNull(summary.descentTime()).toMillis(), 1);
        assertEquals(25398, Objects.requireNonNull(summary.ascentTime()).toMillis(), 1);
        assertEquals(0.052, Objects.requireNonNull(summary.avgAscentRate()), 0.0001);
        // Percent -> fraction conversions (scale is genuinely 1 for these, unlike the fields
        // above) are unchanged and still correct.
        assertEquals(0.03, Objects.requireNonNull(summary.startCNS()), 0.0001);
        assertEquals(0.04, Objects.requireNonNull(summary.endCNS()), 0.0001);
        // o2Toxicity's FIT unit is OTUs, not percent - passed through unscaled now.
        assertEquals(3.0, Objects.requireNonNull(summary.o2Toxicity()), 0.0001);
    }

    // getSummary(FitMessages) is the fallback layer added for Suunto's FIT export, which never
    // emits DiveSummaryMesg at all (see SuuntoFitCharacterizationTest for the empirical proof, and
    // SuuntoFitReaderServiceTest for the same coverage against a real Suunto fixture end-to-end) -
    // these two tests exercise it directly with mocks, isolated from everything else that changes
    // between a Garmin- and Suunto-shaped file.

    @Test
    void getSummaryPrefersDiveSummaryMesgsOverSessionFallbackWhenBothArePresent() {
        final var messages = mock(FitMessages.class);
        final var first = mock(DiveSummaryMesg.class);
        when(first.getTimestamp()).thenReturn(new DateTime(Date.from(Instant.EPOCH)));
        final var last = mock(DiveSummaryMesg.class);
        when(last.getTimestamp())
                .thenReturn(new DateTime(Date.from(Instant.EPOCH.plusSeconds(60))));
        when(last.getAvgDepth()).thenReturn(5.0f);
        when(last.getMaxDepth()).thenReturn(9.0f);
        when(last.getSurfaceInterval()).thenReturn(0L);
        when(last.getBottomTime()).thenReturn(60.0f);
        when(messages.getDiveSummaryMesgs()).thenReturn(List.of(first, last));
        // A session that would produce an obviously different result if it were used instead -
        // proves the DiveSummaryMesg path really does win when both are available.
        final var session = mock(com.garmin.fit.SessionMesg.class);
        when(session.getStartTime()).thenReturn(new DateTime(Date.from(Instant.EPOCH)));
        when(session.getTimestamp())
                .thenReturn(new DateTime(Date.from(Instant.EPOCH.plusSeconds(999))));
        when(messages.getSessionMesgs()).thenReturn(List.of(session));

        final var summary = service.getSummary(messages).orElseThrow();

        assertEquals(9.0, summary.maxDepth(), 0.0001);
        assertEquals(Instant.EPOCH.plusSeconds(60), summary.end());
    }

    @Test
    void getSummaryFallsBackToSessionAndRecordsWhenNoDiveSummaryMesgsExist() {
        final var messages = mock(FitMessages.class);
        when(messages.getDiveSummaryMesgs()).thenReturn(List.of());
        final var start = Instant.parse("2026-08-22T08:13:39Z");
        final var end = Instant.parse("2026-08-22T09:18:29Z");
        final var session = mock(com.garmin.fit.SessionMesg.class);
        when(session.getStartTime()).thenReturn(new DateTime(Date.from(start)));
        when(session.getTimestamp()).thenReturn(new DateTime(Date.from(end)));
        when(messages.getSessionMesgs()).thenReturn(List.of(session));
        final var shallow = recordAt(start);
        final var shallowDepth = depthFieldOf(10.0);
        when(shallow.getField(RecordMesg.DepthFieldNum)).thenReturn(shallowDepth);
        final var deep = recordAt(start.plusSeconds(60));
        final var deepDepth = depthFieldOf(20.0);
        when(deep.getField(RecordMesg.DepthFieldNum)).thenReturn(deepDepth);
        when(messages.getRecordMesgs()).thenReturn(List.of(shallow, deep));

        final var summary = service.getSummary(messages).orElseThrow();

        assertEquals(start, summary.start());
        assertEquals(end, summary.end());
        assertEquals(15.0, summary.averageDepth(), 0.0001);
        assertEquals(20.0, summary.maxDepth(), 0.0001);
        assertEquals(Duration.between(start, end), summary.bottomTime());
        assertNull(summary.descentTime());
        assertNull(summary.startCNS());
    }

    @Test
    void getSummaryIsEmptyWhenNeitherDiveSummaryMesgsNorASessionAreUsable() {
        final var messages = mock(FitMessages.class);
        when(messages.getDiveSummaryMesgs()).thenReturn(List.of());
        when(messages.getSessionMesgs()).thenReturn(List.of());

        assertEquals(Optional.empty(), service.getSummary(messages));
    }

    private static com.garmin.fit.Field depthFieldOf(final double value) {
        final var field = mock(com.garmin.fit.Field.class);
        when(field.getDoubleValue()).thenReturn(value);
        return field;
    }

    @Test
    void getDecoDoesNotDoubleScaleNextStopDepth() {
        final var record = mock(RecordMesg.class);
        when(record.getNextStopDepth()).thenReturn(4.5f); // already-scaled metres
        when(record.getNextStopTime()).thenReturn(180L);

        final var deco = service.getDeco(record);

        assertEquals(1, deco.size());
        assertEquals(4.5, deco.getFirst().depth(), 0.0001);
        assertEquals(180L, deco.getFirst().seconds());
    }

    @Test
    void getDecoReturnsEmptyWhenNoStopIsRequired() {
        final var record = mock(RecordMesg.class);
        when(record.getNextStopDepth()).thenReturn(0.0f);
        when(record.getNextStopTime()).thenReturn(0L);

        assertEquals(List.of(), service.getDeco(record));
    }

    // Real personal dive-computer exports, deliberately .gitignore'd (see root .gitignore) - never
    // committed, so these only run for whoever has copies of these files sitting in
    // src/test/resources locally. Assumptions.assumeTrue (not @Disabled) means the test actually
    // verifies FIT-parsing correctness against real files for anyone who does have them, while
    // still skipping cleanly (not failing) in CI/for anyone else, where the files are absent.

    // Returns a DiveService mock whose createDiveComputer(...) actually returns a usable
    // DiveComputer instead of Mockito's default null - a bare mock(DiveService.class) makes
    // FitReaderService.parse() throw IllegalStateException("Expected to save computers...") for
    // any real fixture, since saveComputers() filters out every null computer.
    private static DiveService diveServiceReturningComputers() {
        final var diveService = mock(DiveService.class);
        final var nextId = new AtomicLong(1);
        when(diveService.createDiveComputer(anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(
                        invocation ->
                                new DiveComputer(
                                        nextId.getAndIncrement(),
                                        new DiveComputerManufacturer(1L, invocation.getArgument(2)),
                                        invocation.getArgument(0),
                                        invocation.getArgument(1),
                                        null));
        return diveService;
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "36 Malapascua Bugtong Bato.fit",
                "51 Exmouth Lighthouse Reef Site Blizzard Ridge South.fit"
            })
    void readFitParsesCoherently(final String filename) throws IOException {
        Assumptions.assumeTrue(
                getClass().getClassLoader().getResource(filename) != null,
                "fixture not present locally: " + filename);
        final var service = new FitReaderService(diveServiceReturningComputers());
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var result =
                    service.parse(
                            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                            filename,
                            inputStream);
            final var profile = result.payload().profiles().getFirst();
            assertTrue(profile.measurements().size() > 1, "expected more than one sample");

            final var depths = profile.measurements().stream().mapToDouble(DiveMeasurement::depth);
            final var maxDepth = depths.max().orElseThrow();
            // Plausible bounds, not exact values - real dive files vary. Per-sample depth was
            // always parsed correctly (see getDiveProfile()'s doc comment) - this is a general
            // end-to-end coherence smoke test, not itself a regression test for the scaling fix
            // (see getSummaryDoesNotDoubleScaleFieldsTheFitSdkAlreadyScales/
            // getDecoDoesNotDoubleScaleNextStopDepth for that).
            assertTrue(maxDepth > 1 && maxDepth < 150, "implausible max depth: " + maxDepth);
            // Deliberately not asserting anything about profile.start()/end() here - real files
            // from these two fixtures have DiveSummaryMesg entries whose first/last timestamps
            // coincide (a device/session quirk unrelated to this fix), so that's not a reliable
            // "how long was this dive" signal across arbitrary real files; per-sample depth above
            // is the robust coherence check.
        }
    }

    @Test
    void readFitMalapascuaGatoIslandTunnelMatchesKnownValues() throws IOException {
        final var filename = "35 Malapascua Gato Island Tunnel.fit";
        Assumptions.assumeTrue(
                getClass().getClassLoader().getResource(filename) != null,
                "fixture not present locally: " + filename);
        final var service = new FitReaderService(diveServiceReturningComputers());
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var result =
                    service.parse(
                            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                            filename,
                            inputStream);
            final var profile = result.payload().profiles().getFirst();
            final var maxDepth =
                    profile.measurements().stream()
                            .mapToDouble(DiveMeasurement::depth)
                            .max()
                            .orElseThrow();
            // Hand-verified via a direct decode of this fixture's per-sample depth field (a
            // different, already-correct code path from the getSummary()/getDeco() fix above -
            // see getSummaryDoesNotDoubleScaleFieldsTheFitSdkAlreadyScales for the actual
            // regression coverage for that bug). This is a plain end-to-end sanity check that
            // this specific real file still parses to the depth its own dive computer recorded.
            assertEquals(19.42, maxDepth, 0.5);
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

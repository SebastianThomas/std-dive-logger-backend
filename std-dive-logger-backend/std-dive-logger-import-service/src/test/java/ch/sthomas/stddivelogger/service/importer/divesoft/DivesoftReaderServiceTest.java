package ch.sthomas.stddivelogger.service.importer.divesoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftCeilingSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDepthSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDive;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftDiveDetailResponse;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftGraphData;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftGraphMix;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftPressureSample;
import ch.sthomas.stddivelogger.model.importer.divesoft.DivesoftTemperatureSample;
import ch.sthomas.stddivelogger.service.DiveService;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

class DivesoftReaderServiceTest {
    private static final JsonMapper objectMapper = new JsonMapper();
    private static final DiveComputer computer =
            new DiveComputer(1L, new DiveComputerManufacturer(1L, "Divesoft"), "serial", "serial");

    private final DivesoftReaderService service = new DivesoftReaderService(mock(DiveService.class));

    private static DivesoftDive loadFixture(final String filename) throws IOException {
        try (final InputStream inputStream =
                DivesoftReaderServiceTest.class.getClassLoader().getResourceAsStream(filename)) {
            final var response = objectMapper.readValue(inputStream, DivesoftDiveDetailResponse.class);
            return response.diveAndMixes().dive();
        }
    }

    @Test
    void mapsDepthTemperaturePo2AndSingleGasMix() throws IOException {
        final var dive = loadFixture("divesoft-dive-1.json");
        final var profile = service.getDiveProfile(computer, dive);

        assertEquals(computer.id(), profile.diveComputerId());
        assertEquals(708, profile.measurements().size());
        assertEquals(profile.start().plus(Duration.ofHours(1).plusMinutes(20).plusSeconds(8)), profile.end());

        final var first = profile.measurements().getFirst();
        assertEquals(1.59, first.depth());
        assertEquals(25.3, first.temperature().celsius(), 0.001);
        assertEquals(0.6636, first.po2().measured(), 0.0001);
        assertEquals(0.7, first.po2().maxSetPoint(), 0.0001);
        assertEquals(0.21, first.gas().o2(), 0.0001);
        assertEquals(0.79, first.gas().n2(), 0.0001);
        assertTrue(first.deco().isEmpty());
        assertNull(first.cns());

        final var last = profile.measurements().getLast();
        assertEquals(5.0, last.cns());
    }

    @Test
    void tracksOutOfOrderGasSwitchesAndHighPo2() throws IOException {
        final var dive = loadFixture("divesoft-dive-2-hyperoxic.json");
        final var profile = service.getDiveProfile(computer, dive);

        assertEquals(827, profile.measurements().size());

        // graphData.mixes arrives out of timestamp order (the initial air mix at timestamp=0 is
        // listed *last*) - these indices pin down that the switches still land in the right place
        // once sorted.
        assertEquals(0.21, profile.measurements().get(0).gas().o2(), 0.0001);
        assertEquals(0.29, profile.measurements().get(699).gas().o2(), 0.0001);
        assertEquals(0.21, profile.measurements().get(729).gas().o2(), 0.0001);

        // A recorded hyperoxic ppO2 spike (~1.83 bar) should come through untouched.
        assertEquals(1.8289, profile.measurements().get(657).po2().measured(), 0.0001);
    }

    @Test
    void ceilingAboveZeroBecomesADecoStop() {
        final var graphData =
                new DivesoftGraphData(
                        List.of(new DivesoftDepthSample(0, 10.0), new DivesoftDepthSample(60, 9.0)),
                        List.of(
                                new DivesoftTemperatureSample(0, 20.0),
                                new DivesoftTemperatureSample(60, 20.0)),
                        List.of(new DivesoftCeilingSample(0, 0.0), new DivesoftCeilingSample(60, 3.0)),
                        List.of(new DivesoftPressureSample(0, 0.7), new DivesoftPressureSample(60, 0.7)),
                        List.of(new DivesoftPressureSample(0, 1.0), new DivesoftPressureSample(60, 1.0)),
                        List.of(),
                        List.of(new DivesoftGraphMix(0, "21", "0", "air")));
        final var dive =
                new DivesoftDive(
                        "synthetic",
                        "serial",
                        "",
                        "",
                        null,
                        null,
                        null,
                        null,
                        "00:01:00",
                        "Mon Jan 1 2024 00:00:00 GMT+0000 (Coordinated Universal Time)",
                        List.of(),
                        null,
                        null,
                        null,
                        graphData);

        final var profile = service.getDiveProfile(computer, dive);

        assertTrue(profile.measurements().get(0).deco().isEmpty());
        assertEquals(
                List.of(new DecoStop("ceiling", 3.0, 0)), profile.measurements().get(1).deco());
    }

    @Test
    void parsesStartDateIgnoringTrailingTimezoneName() {
        final var instant =
                DivesoftReaderService.parseStartDate(
                        "Sat May 30 2026 23:21:05 GMT+0000 (Coordinated Universal Time)");
        assertEquals(Instant.parse("2026-05-30T23:21:05Z"), instant);
    }

    @Test
    void parsesHhMmSsDuration() {
        assertEquals(
                Duration.ofHours(1).plusMinutes(20).plusSeconds(8),
                DivesoftReaderService.parseDuration("01:20:08"));
        assertNull(DivesoftReaderService.parseDuration(null));
    }
}

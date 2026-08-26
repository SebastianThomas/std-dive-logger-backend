package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.dataformat.xml.XmlMapper;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GraphImageCreatorTest {
    private static final XmlMapper xmlMapper = xmlMapper();
    private static final Logger logger = LoggerFactory.getLogger(GraphImageCreatorTest.class);

    private static DiveSummary testSummary(final Instant start, final Instant end) {
        return new DiveSummary(start, end, 0.0, 0, null, Duration.ZERO, null);
    }

    @Test
    @Disabled("MD5 not the same on Mac as on Github Runner")
    public void createGraphImage() throws IOException {
        final var start =
                LocalDateTime.of(2025, Month.NOVEMBER, 17, 15, 25, 0).toInstant(ZoneOffset.UTC);
        final var end = start.plus(1, ChronoUnit.MINUTES);
        final var computer =
                new DiveComputer(
                        0, new DiveComputerManufacturer(1, "Manufacturer"), "SN", "Computer", null);
        final var ndl = Duration.ofMinutes(99);
        final var fifteenC = new Temperature(15, Temperature.TemperatureUnit.CELSIUS);
        final var measurements =
                List.of(
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start, fifteenC, 1, ndl, null, Gas.AIR, null, null, null,
                                        null, null, null, null),
                                0),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(5),
                                        fifteenC,
                                        1.3,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                1),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(10),
                                        fifteenC,
                                        1.5,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                2),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(15),
                                        fifteenC,
                                        1.8,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                3),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(20),
                                        fifteenC,
                                        2.1,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                4),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(25),
                                        fifteenC,
                                        2.5,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                5),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(30),
                                        fifteenC,
                                        3.0,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                6),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(35),
                                        fifteenC,
                                        2.2,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                7),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(40),
                                        fifteenC,
                                        1.5,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                8),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(45),
                                        fifteenC,
                                        1.0,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                9),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(50),
                                        fifteenC,
                                        0.5,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                10),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(55),
                                        fifteenC,
                                        0.2,
                                        ndl,
                                        null,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                11),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        end, fifteenC, 0, ndl, null, Gas.AIR, null, null, null,
                                        null, null, null, null),
                                12));
        final var profiles = List.of(new DiveProfile(0, computer, start, end, measurements, null));
        final var testDive =
                new Dive(
                        0,
                        new FrontendUser(1, "TestName", null, null),
                        1,
                        "Some Dive",
                        "test-dive",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        profiles,
                        List.of(),
                        List.of(new NamedBuddy(1, "Buddy1", null)),
                        testSummary(start, end),
                        List.of(),
                        null,
                        null,
                        DiveLeader.SELF,
                        null);
        final var tempFile = Files.createTempFile("test_dive_profile-", ".svg").toFile();
        try (final var outWriter = new FileWriter(tempFile)) {
            GraphImageCreator.fromDive(
                    testDive,
                    outWriter,
                    Map.ofEntries(
                            Map.entry(
                                    DiveMeasurement.DiveMeasurementProperty.TEMPERATURE,
                                    Pair.of(
                                            m -> Objects.requireNonNull(m.temperature()).celsius(),
                                            LegendType.RIGHT)),
                            Map.entry(
                                    DiveMeasurement.DiveMeasurementProperty.DEPTH,
                                    Pair.of(DiveMeasurement::depth, LegendType.LEFT))),
                    new Dimension(800, 450));
            logger.info("Created temp file with Dive Profile: {}", tempFile.getAbsolutePath());
            try (final var expected =
                    getClass()
                            .getClassLoader()
                            .getResourceAsStream("test-dive-profile-expected.svg")) {
                assertEquals(
                        DigestUtils.md5Hex(Objects.requireNonNull(expected)),
                        DigestUtils.md5Hex(
                                Files.newInputStream(tempFile.toPath().toAbsolutePath())));
            }
        }
    }

    @Test
    public void createGraphImageShadesDecoZoneOnlyWhenDecoObligationPresent() throws IOException {
        final var start =
                LocalDateTime.of(2025, Month.NOVEMBER, 17, 15, 25, 0).toInstant(ZoneOffset.UTC);
        final var fifteenC = new Temperature(15, Temperature.TemperatureUnit.CELSIUS);
        final var noDeco = List.<DecoStop>of();
        final var withDeco = List.of(new DecoStop("DECO", 6.0, 180));

        final var measurements =
                List.of(
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start, fifteenC, 1, null, noDeco, Gas.AIR, null, null, null,
                                        null, null, null, null),
                                0),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(5),
                                        fifteenC,
                                        20,
                                        null,
                                        withDeco,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                1),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(10),
                                        fifteenC,
                                        18,
                                        null,
                                        withDeco,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                2),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start.plusSeconds(15),
                                        fifteenC,
                                        0,
                                        null,
                                        noDeco,
                                        Gas.AIR,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null),
                                3));
        final var computer =
                new DiveComputer(
                        0, new DiveComputerManufacturer(1, "Manufacturer"), "SN", "Computer", null);
        final var end = start.plusSeconds(15);
        final var profiles = List.of(new DiveProfile(0, computer, start, end, measurements, null));
        final var testDive =
                new Dive(
                        0,
                        new FrontendUser(1, "TestName", null, null),
                        1,
                        "Some Dive",
                        "test-dive",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        profiles,
                        List.of(),
                        List.of(),
                        testSummary(start, end),
                        List.of(),
                        null,
                        null,
                        DiveLeader.SELF,
                        null);

        final var withDecoWriter = new StringWriter();
        GraphImageCreator.fromDive(
                testDive,
                withDecoWriter,
                Map.ofEntries(
                        Map.entry(
                                DiveMeasurement.DiveMeasurementProperty.DEPTH,
                                Pair.of(DiveMeasurement::depth, LegendType.LEFT))),
                new Dimension(500, 200));
        assertTrue(
                withDecoWriter.toString().contains("fill:rgb(220,38,38)"),
                "expected a deco zone shape when the profile has an active mandatory stop");

        final var noDecoMeasurements =
                measurements.stream()
                        .map(
                                m ->
                                        new DiveMeasurementWithId(
                                                new DiveMeasurement(
                                                        m.measurement().time(),
                                                        m.measurement().temperature(),
                                                        m.measurement().depth(),
                                                        null,
                                                        noDeco,
                                                        Gas.AIR,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null),
                                                m.id()))
                        .toList();
        final var noDecoDive =
                new Dive(
                        testDive.id(),
                        testDive.user(),
                        testDive.number(),
                        testDive.notes(),
                        testDive.customIdentifier(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new DiveProfile(0, computer, start, end, noDecoMeasurements, null)),
                        List.of(),
                        List.of(),
                        testDive.summary(),
                        List.of(),
                        null,
                        null,
                        DiveLeader.SELF,
                        null);
        final var noDecoWriter = new StringWriter();
        GraphImageCreator.fromDive(
                noDecoDive,
                noDecoWriter,
                Map.ofEntries(
                        Map.entry(
                                DiveMeasurement.DiveMeasurementProperty.DEPTH,
                                Pair.of(DiveMeasurement::depth, LegendType.LEFT))),
                new Dimension(500, 200));
        assertFalse(
                noDecoWriter.toString().contains("fill:rgb(220,38,38)"),
                "no deco zone shape should be drawn when nothing in the profile is in deco");
    }

    public static XmlMapper xmlMapper() {
        return ObjectMapperUtils.xmlMapperBuilder(customizer -> {}).build();
    }
}

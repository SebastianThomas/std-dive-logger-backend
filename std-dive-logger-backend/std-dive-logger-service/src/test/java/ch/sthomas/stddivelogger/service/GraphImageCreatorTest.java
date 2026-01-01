package ch.sthomas.stddivelogger.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.sthomas.stddivelogger.model.dive.*;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Gas;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.Temperature;
import ch.sthomas.stddivelogger.model.graphs.LegendType;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.service.process.GraphImageCreator;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
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

    @Test
    @Disabled("MD5 not the same on Mac as on Github Runner")
    public void createGraphImage() throws IOException {
        final var start =
                LocalDateTime.of(2025, Month.NOVEMBER, 17, 15, 25, 0).toInstant(ZoneOffset.UTC);
        final var end = start.plus(1, ChronoUnit.MINUTES);
        final var computer = new DiveComputer(0, null, "SN", "Computer");
        final var ndl = Duration.ofMinutes(99);
        final var fifteenC = new Temperature(15, Temperature.TemperatureUnit.CELSIUS);
        final var measurements =
                List.of(
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        start, fifteenC, 1, ndl, null, Gas.AIR, null, null, null,
                                        null),
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
                                        null),
                                11),
                        new DiveMeasurementWithId(
                                new DiveMeasurement(
                                        end, fifteenC, 0, ndl, null, Gas.AIR, null, null, null,
                                        null),
                                12));
        final var profiles = List.of(new DiveProfile(0, computer, start, end, measurements, null));
        final var testDive =
                new Dive(
                        0,
                        new FrontendUser(1, "TestName"),
                        1,
                        "Some Dive",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        profiles,
                        List.of(),
                        List.of("Buddy1"),
                        null);
        final var tempFile = Files.createTempFile("test_dive_profile-", ".svg").toFile();
        try (final var outWriter = new FileWriter(tempFile)) {
            GraphImageCreator.fromDive(
                    testDive,
                    outWriter,
                    Map.ofEntries(
                            Map.entry(
                                    DiveMeasurement.DiveMeasurementProperty.TEMPERATURE,
                                    Pair.of(m -> m.temperature().celsius(), LegendType.RIGHT)),
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

    public static XmlMapper xmlMapper() {
        final var xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new Jdk8Module());
        xmlMapper.registerModule(new JavaTimeModule());
        return xmlMapper;
    }
}

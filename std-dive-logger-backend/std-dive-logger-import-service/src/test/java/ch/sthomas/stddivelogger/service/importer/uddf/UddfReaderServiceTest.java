package ch.sthomas.stddivelogger.service.importer.uddf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.stats.DiveGasConsumption;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.function.UnaryOperator;

class UddfReaderServiceTest {
    private static final XmlMapper xmlMapper = ObjectMapperUtils.xmlMapperBuilder(c -> {}).build();
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);

    // A well-formed-ish UDDF document (correct version, generator, diver) but missing the entire
    // <profiledata> section, which real malformed/incomplete exports can omit.
    private static String missingProfileDataXml() {
        return """
                <uddf version="3.2.0">
                  <generator>
                    <name>Test</name>
                    <version>1.0</version>
                    <datetime>2024-01-01T00:00:00Z</datetime>
                  </generator>
                  <diver>
                    <owner>
                      <personal>
                        <firstname>Test</firstname>
                        <lastname>Diver</lastname>
                      </personal>
                    </owner>
                  </diver>
                </uddf>
                """;
    }

    @Test
    void missingProfileDataSectionReportsACleanErrorInsteadOfThrowing() throws IOException {
        final var service = new UddfReaderService(xmlMapper, mock(DiveService.class));
        try (final var inputStream =
                new ByteArrayInputStream(
                        missingProfileDataXml().getBytes(StandardCharsets.UTF_8))) {
            final var result =
                    assertDoesNotThrow(
                            () ->
                                    service.parse(user, "no-profiledata.uddf", inputStream)
                                            .reduce(ParsedImportResultStreaming::concat)
                                            .orElseThrow()
                                            .toResult());

            assertEquals(0, result.parsed().size());
            assertEquals(1, result.errors().size());
            assertTrue(result.errors().getFirst().contains("no-profiledata.uddf"));
        }
    }

    @Test
    void keepsTheFilesDecoModelAndSurfacePressure() throws IOException {
        try (final var inputStream =
                UddfReaderServiceTest.class
                        .getClassLoader()
                        .getResourceAsStream("shearwater-perdix2.uddf")) {
            final var settings =
                    Objects.requireNonNull(
                            xmlMapper
                                    .readValue(Objects.requireNonNull(inputStream), UddfFile.class)
                                    .exportDecoSettings(0));

            assertThat(settings.algorithm()).isEqualTo("Bühlmann ZHL-16C");
            assertThat(settings.gfLow()).isEqualTo(50);
            assertThat(settings.gfHigh()).isEqualTo(85);
            assertThat(settings.surfacePressureMbar()).isEqualTo(978.0);
            assertThat(settings.details()).containsEntry("decomodel", "buehlmann:zhl16c");
        }
    }

    private static UddfFile fixture(final String name) throws IOException {
        return fixture(name, xml -> xml);
    }

    private static UddfFile fixture(final String name, final UnaryOperator<String> patch)
            throws IOException {
        try (final var inputStream =
                UddfReaderServiceTest.class.getClassLoader().getResourceAsStream(name)) {
            final var xml =
                    new String(
                            Objects.requireNonNull(inputStream).readAllBytes(),
                            StandardCharsets.UTF_8);
            return xmlMapper.readValue(patch.apply(xml), UddfFile.class);
        }
    }

    /** Adds a mix link and/or volume to the first {@code <tankdata>}. */
    private static UnaryOperator<String> firstTank(final String extra) {
        return xml -> xml.replaceFirst("<tankdata>", "<tankdata>" + extra);
    }

    @Test
    void singleGasDiveTurnsEachTankWithAPressureDropIntoACylinderOfThatGas() throws IOException {
        final var file = fixture("shearwater-perdix2.uddf");

        final var cylinders = file.exportConfiguration(user, 0).cylinders();

        // Two of six tankdata carry pressures (210->115, 195->105 bar); the rest are all-zero.
        assertThat(cylinders).hasSize(2);
        assertThat(cylinders)
                .allSatisfy(
                        c -> {
                            assertThat(c.gas().o2()).isEqualTo(0.21);
                            assertThat(c.role()).isEqualTo(CylinderRole.OC);
                            // Shearwater's UDDF has no tankvolume.
                            assertThat(c.size().liters()).isZero();
                        });
        assertThat(cylinders.getFirst().startBar()).isCloseTo(209.95, within(0.01));
        assertThat(cylinders.getFirst().endBar()).isCloseTo(114.94, within(0.01));
        assertThat(cylinders.get(1).startBar()).isCloseTo(194.98, within(0.01));
        assertThat(cylinders.get(1).endBar()).isCloseTo(104.94, within(0.01));
        assertThat(file.exportGasConsumption(0)).isEqualTo(DiveGasConsumption.EMPTY);
    }

    @Test
    void multiGasDiveWithoutMixLinksLeavesTheTanksOut() throws IOException {
        // 21%, 60% and 50% all breathed, three tanks with pressures, none linked to a mix.
        final var file = fixture("Discover Greece - Tsolis Wall.uddf");

        assertThat(file.exportConfiguration(user, 0).cylinders()).isEmpty();
        assertThat(file.exportGasConsumption(0)).isEqualTo(DiveGasConsumption.EMPTY);
    }

    @Test
    void aLinkedMixAndVolumeAreTakenAsGiven() throws IOException {
        final var file =
                fixture(
                        "shearwater-perdix2.uddf",
                        firstTank("<link ref=\"OC1:50/00\" /><tankvolume>0.012</tankvolume>"));

        final var cylinders = file.exportConfiguration(user, 0).cylinders();

        assertThat(cylinders.getFirst().gas().o2()).isEqualTo(0.5);
        assertThat(cylinders.getFirst().size().liters()).isCloseTo(12.0, within(1e-9));
        assertThat(cylinders.get(1).gas().o2()).isEqualTo(0.21);
    }

    @Test
    void uncylinderedTanksReportSurfaceLitresFromVolumeTimesPressureDrop() throws IOException {
        final var file =
                fixture(
                        "Discover Greece - Tsolis Wall.uddf",
                        firstTank("<tankvolume>0.012</tankvolume>"));

        // 12 L x (19498380 - 10997142) Pa = 12 L x 85.01238 bar.
        assertThat(file.exportGasConsumption(0).totalLiters())
                .isCloseTo(12 * 85.01238, within(1e-6));
    }
}

package ch.sthomas.stddivelogger.model;

import static org.junit.jupiter.api.Assertions.*;

import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile;
import ch.sthomas.stddivelogger.model.importer.UddfFile;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class TestImport {
    private static final Logger logger = LoggerFactory.getLogger(TestImport.class);
    private static final XmlMapper xmlMapper = xmlMapper();

    @ParameterizedTest
    @CsvSource(
            value = {
                "Perdix_2_A3B6F031__42_2024-12-1_15-24-0.uddf, Ledi-Wracks",
                "Petrel[3699F336]#98.1 2026-1-6 15-57-9.uddf, "
            })
    void testImportUddf(final String filename, final String diveSite) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var content = xmlMapper.readValue(inputStream, UddfFile.class);
            logger.info("Reading from UDDF {}", filename);
            assertNotNull(content);
            assertEquals(diveSite, content.exportSite());
            if (filename.startsWith("Petrel")) {
                // CCR Test
                assertFalse(
                        content.exportMeasurements().stream()
                                .map(DiveMeasurement::po2)
                                .map(Optional::ofNullable)
                                .flatMap(Optional::stream)
                                .findFirst()
                                .isEmpty());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = "felice_2025_10_18.xml")
    @Disabled
    void testImportSubsurfaceXml(final String filename) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var content = xmlMapper.readValue(inputStream, SubsurfaceXmlFile.class);
            logger.info("Reading from Subsurface XML {}", filename);
            assertNotNull(content);
            assertEquals(313, content.dives().size());
        }
    }

    public static XmlMapper xmlMapper() {
        final var xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new Jdk8Module());
        xmlMapper.registerModule(new JavaTimeModule());
        return xmlMapper;
    }
}

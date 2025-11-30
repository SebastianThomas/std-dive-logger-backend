package ch.sthomas.stddivelogger.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ch.sthomas.stddivelogger.model.importer.SubsurfaceXmlFile;
import ch.sthomas.stddivelogger.model.importer.UddfFile;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestImport {
    private static final Logger logger = LoggerFactory.getLogger(TestImport.class);
    private static final XmlMapper xmlMapper = xmlMapper();

    @ParameterizedTest
    @ValueSource(strings = "Perdix_2_A3B6F031__42_2024-12-1_15-24-0.uddf")
    void testImportUddf(final String filename) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var content = xmlMapper.readValue(inputStream, UddfFile.class);
            logger.info("Reading from {}", filename);
            assertNotNull(content);
            assertEquals("Ledi-Wracks", content.exportSite());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = "felice_2025_10_18.xml")
    void testImportSubsurfaceXml(final String filename) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var content = xmlMapper.readValue(inputStream, SubsurfaceXmlFile.class);
            logger.info("Reading from {}", filename);
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

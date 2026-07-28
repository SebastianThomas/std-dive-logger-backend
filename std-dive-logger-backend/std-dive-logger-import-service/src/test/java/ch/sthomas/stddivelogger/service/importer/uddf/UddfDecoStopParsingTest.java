package ch.sthomas.stddivelogger.service.importer.uddf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.sthomas.stddivelogger.model.dive.profile.DecoStop;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.importer.UddfFile;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.util.List;

class UddfDecoStopParsingTest {
    private static final String FILE = "Discover Greece - Tsolis Wall.uddf";

    private static XmlMapper xmlMapper() {
        return ObjectMapperUtils.xmlMapperBuilder(customizer -> {}).build();
    }

    @Test
    void mandatoryDecoStopsAreParsed() throws IOException {
        final UddfFile file;
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(FILE)) {
            file = xmlMapper().readValue(inputStream, UddfFile.class);
        }

        final List<DiveMeasurement> measurements = file.exportMeasurements(0);
        final var withDeco = measurements.stream().filter(m -> !m.deco().isEmpty()).toList();

        assertFalse(withDeco.isEmpty(), "Expected at least one measurement with deco stops");

        final DecoStop firstStop = withDeco.getFirst().deco().getFirst();
        assertEquals("mandatory", firstStop.type());
        assertEquals(6, firstStop.depth());
        assertTrue(firstStop.seconds() > 0);
    }
}

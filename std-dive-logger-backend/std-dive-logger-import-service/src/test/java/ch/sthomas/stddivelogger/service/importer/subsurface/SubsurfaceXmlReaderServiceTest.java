package ch.sthomas.stddivelogger.service.importer.subsurface;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.model.dive.gear.CylinderRole;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.CylinderSizeUnit;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.ParsedImportResultStreaming;
import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import tools.jackson.dataformat.xml.XmlMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

class SubsurfaceXmlReaderServiceTest {
    private static final XmlMapper xmlMapper = ObjectMapperUtils.xmlMapperBuilder(c -> {}).build();
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);

    private static String duplicateSiteUuidXml() {
        // Two <site> elements sharing the same uuid, which can happen with manually
        // merged/edited Subsurface exports.
        return """
                <divelog>
                  <divesites uuid="dup-uuid" name="First Site" gps="1.0 2.0"/>
                  <divesites uuid="dup-uuid" name="Second Site" gps="3.0 4.0"/>
                  <dives>
                    <dive number="1" date="2024-01-01" time="10:00:00" duration="0:10 min" \
                rating="0" divesiteid="dup-uuid" visibility="0" current="0">
                      <buddy>Test Buddy</buddy>
                      <cylinder description="AL80" o2="21%"/>
                      <divecomputer model="Test 123" deviceid="1" diveid="1" date="2024-01-01" \
                time="10:00:00" duration="0:10 min">
                        <event time="0:00 min" type="0" name="gaschange" cylinder="0"/>
                        <sample time="0:00 min" depth="0.0 m"/>
                      </divecomputer>
                    </dive>
                  </dives>
                </divelog>
                """;
    }

    private DiveService diveServiceReturningComputer() {
        final var diveService = mock(DiveService.class);
        Mockito.when(
                        diveService.getOrCreateDiveComputer(
                                Mockito.eq(user),
                                Mockito.anyString(),
                                Mockito.anyString(),
                                Mockito.anyString()))
                .thenReturn(
                        new DiveComputer(
                                1L,
                                new DiveComputerManufacturer(1L, "Test"),
                                "1",
                                "Test 123",
                                null));
        return diveService;
    }

    @Test
    void duplicateDiveSiteUuidDoesNotAbortTheWholeFileImport() throws IOException {
        final var service =
                new SubsurfaceXmlReaderService(xmlMapper, diveServiceReturningComputer());
        try (final var inputStream =
                new ByteArrayInputStream(duplicateSiteUuidXml().getBytes(StandardCharsets.UTF_8))) {
            final var result =
                    assertDoesNotThrow(
                            () ->
                                    service.parse(user, "duplicate-sites.xml", inputStream)
                                            .reduce(ParsedImportResultStreaming::concat)
                                            .orElseThrow()
                                            .toResult());

            // The dive itself parses successfully (using whichever of the two duplicate sites
            // Collectors.toMap's merge function kept), it is not swallowed as an error.
            assertEquals(1, result.parsed().size());
            assertEquals(0, result.errors().size());
        }
    }

    private static String withEndOfDiveTotalsXml() {
        // otu/cns are attributes on <dive>, not child elements - the whole point of this test.
        return """
                <divelog>
                  <divesites uuid="site-1" name="Test Site" gps="1.0 2.0"/>
                  <dives>
                    <dive number="1" date="2024-01-01" time="10:00:00" duration="0:20 min" \
                rating="0" divesiteid="site-1" visibility="0" current="0" otu="12" cns="11%">
                      <buddy>Test Buddy</buddy>
                      <cylinder description="AL80" o2="21%"/>
                      <divecomputer model="Test 123" deviceid="1" diveid="1" date="2024-01-01" \
                time="10:00:00" duration="0:20 min">
                        <event time="0:00 min" type="0" name="gaschange" cylinder="0"/>
                        <sample time="0:00 min" depth="0.0 m"/>
                        <sample time="0:10 min" depth="10.0 m"/>
                        <sample time="0:20 min" depth="0.0 m"/>
                      </divecomputer>
                    </dive>
                  </dives>
                </divelog>
                """;
    }

    @Test
    void endOfDiveOtuAndCnsAreStampedOntoTheLastMeasurementOnly() throws IOException {
        final var service =
                new SubsurfaceXmlReaderService(xmlMapper, diveServiceReturningComputer());
        try (final var inputStream =
                new ByteArrayInputStream(
                        withEndOfDiveTotalsXml().getBytes(StandardCharsets.UTF_8))) {
            final var result =
                    service.parse(user, "end-of-dive-totals.xml", inputStream)
                            .reduce(ParsedImportResultStreaming::concat)
                            .orElseThrow()
                            .toResult();

            assertEquals(1, result.parsed().size());
            final var measurements =
                    result.parsed().getFirst().payload().profiles().getFirst().measurements();
            assertEquals(3, measurements.size());
            // Every sample before the last one is untouched - no per-sample cns/otu in Subsurface.
            assertEquals(null, measurements.get(0).o2Tox());
            assertEquals(null, measurements.get(0).cns());
            assertEquals(null, measurements.get(1).o2Tox());
            assertEquals(null, measurements.get(1).cns());
            // The dive-level total lands on the last sample only.
            assertEquals(12.0, measurements.getLast().o2Tox());
            assertEquals(11.0, measurements.getLast().cns());
        }
    }

    private static String withCylinderDetailsXml() {
        // A trimix-ish back gas with both start and end pressure, plus a bailout cylinder whose
        // pressure is given in psi to verify unit conversion.
        return """
                <divelog>
                  <divesites uuid="site-1" name="Test Site" gps="1.0 2.0"/>
                  <dives>
                    <dive number="1" date="2024-01-01" time="10:00:00" duration="0:10 min" \
                rating="0" divesiteid="site-1" visibility="0" current="0">
                      <buddy>Test Buddy</buddy>
                      <cylinder description="Backgas" size="12.0 l" workpressure="232.0 bar" \
                start="200 bar" end="80 bar" o2="21%" he="35%"/>
                      <cylinder description="Bailout" size="80.0 cuft" workpressure="3000.0 psi" \
                start="3000 psi" end="2000 psi" o2="32%"/>
                      <divecomputer model="Test 123" deviceid="1" diveid="1" date="2024-01-01" \
                time="10:00:00" duration="0:10 min">
                        <event time="0:00 min" type="0" name="gaschange" cylinder="0"/>
                        <sample time="0:00 min" depth="0.0 m"/>
                      </divecomputer>
                    </dive>
                  </dives>
                </divelog>
                """;
    }

    @Test
    void cylinderSizePressureAndGasMixAreImportedIntoTheDiveConfiguration() throws IOException {
        final var service =
                new SubsurfaceXmlReaderService(xmlMapper, diveServiceReturningComputer());
        try (final var inputStream =
                new ByteArrayInputStream(
                        withCylinderDetailsXml().getBytes(StandardCharsets.UTF_8))) {
            final var result =
                    service.parse(user, "cylinder-details.xml", inputStream)
                            .reduce(ParsedImportResultStreaming::concat)
                            .orElseThrow()
                            .toResult();

            assertEquals(1, result.parsed().size());
            final var cylinders = result.parsed().getFirst().payload().configuration().cylinders();
            assertEquals(2, cylinders.size());

            final var backgas = cylinders.get(0);
            assertEquals(CylinderSizeUnit.LITER, backgas.size().unit());
            assertEquals(12.0, backgas.size().value());
            assertEquals(200.0, Objects.requireNonNull(backgas.startBar()));
            assertEquals(80.0, Objects.requireNonNull(backgas.endBar()));
            assertEquals(0.21, backgas.gas().o2(), 1e-9);
            assertEquals(0.35, backgas.gas().he(), 1e-9);
            assertEquals(CylinderRole.OC, backgas.role());
            assertEquals(null, backgas.usageStart());
            assertEquals(null, backgas.usageEnd());

            final var bailout = cylinders.get(1);
            assertEquals(CylinderSizeUnit.CUFT, bailout.size().unit());
            // psi is converted to bar for start/end pressure.
            assertEquals(3000.0 / 14.5038, Objects.requireNonNull(bailout.startBar()), 1e-6);
            assertEquals(2000.0 / 14.5038, Objects.requireNonNull(bailout.endBar()), 1e-6);
            assertEquals(0.32, bailout.gas().o2(), 1e-9);
            assertEquals(0.0, bailout.gas().he(), 1e-9);
        }
    }
}

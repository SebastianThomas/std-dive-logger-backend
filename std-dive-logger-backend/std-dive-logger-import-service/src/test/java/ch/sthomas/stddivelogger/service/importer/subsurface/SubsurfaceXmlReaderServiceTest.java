package ch.sthomas.stddivelogger.service.importer.subsurface;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
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
                                1L, new DiveComputerManufacturer(1L, "Test"), "1", "Test 123"));
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
}

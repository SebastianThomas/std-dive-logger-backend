package ch.sthomas.stddivelogger.service.importer.uddf;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
}

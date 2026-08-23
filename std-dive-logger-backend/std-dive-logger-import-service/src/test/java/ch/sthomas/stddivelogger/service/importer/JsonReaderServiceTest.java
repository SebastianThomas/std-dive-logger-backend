package ch.sthomas.stddivelogger.service.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;
import ch.sthomas.stddivelogger.service.importer.suunto.SuuntoJsonReaderService;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/** A ".json" upload is not necessarily a Suunto (or any known) export - see JsonReaderService. */
class JsonReaderServiceTest {

    private static DiveService diveServiceReturningComputer() {
        final var diveService = mock(DiveService.class);
        when(diveService.getOrCreateDiveComputer(any(), anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocation ->
                                new DiveComputer(
                                        1L,
                                        new DiveComputerManufacturer(1L, invocation.getArgument(1)),
                                        invocation.getArgument(2),
                                        invocation.getArgument(3),
                                        null));
        return diveService;
    }

    private final JsonReaderService service =
            new JsonReaderService(new SuuntoJsonReaderService(diveServiceReturningComputer()));

    private ParsedImport parse(final String json) throws IOException {
        return service.parse(
                new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null),
                "test.json",
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void notJsonAtAllFailsWithAGenericMessageNotARawJacksonException() {
        assertThatThrownBy(() -> parse("this is not json at all"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.json")
                .hasMessageNotContaining("Unrecognized")
                .hasMessageNotContaining("token");
    }

    @Test
    void validJsonOfAnUnknownShapeIsRejectedWithoutAssumingABrand() {
        // Some other, unrelated but valid JSON - must not be mistaken for Suunto just because it
        // has a .json extension, and the message must not name Suunto or any internal field name.
        assertThatThrownBy(
                        () ->
                                parse(
                                        """
                {"id":"abc123","deviceSerial":"999"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test.json")
                .hasMessageNotContaining("Suunto")
                .hasMessageNotContaining("DeviceLog");
    }

    @Test
    void aSuuntoShapedExportIsDispatchedToTheSuuntoReader() throws IOException {
        final var json =
                """
                {"DeviceLog":{"Header":{"DateTime":"2026-01-01T10:00:00.000+00:00",
                "Device":{"Name":"EON Core","SerialNumber":"1"},"Duration":20.0},"Samples":[]}}
                """;

        final var result = parse(json);

        assertThat(result.source()).isEqualTo(PendingImportSource.JSON_SUUNTO);
        assertThat(result.payload().profiles()).hasSize(1);
    }
}

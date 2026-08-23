package ch.sthomas.stddivelogger.service.importer.dl7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.sthomas.stddivelogger.model.controller.dive.PendingImportSource;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputerManufacturer;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.DiveService;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * Real, anonymized (device serial replaced) DL7 ("Universal Dive Data Format") export - the same
 * physical dive as the sibling {@code shearwater-perdix2-native.xml}/{@code .uddf} fixtures. See
 * Dl7Export's doc comment for why this format's per-sample deco/TTS columns aren't parsed.
 */
class Dl7ReaderServiceTest {
    private static final User user =
            new User(0, "", "", "", true, Instant.now(), Instant.now(), null, null);
    private static final String FIXTURE = "shearwater-perdix2.zxu";

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

    private static ch.sthomas.stddivelogger.service.importer.ParsedImport parseFixture()
            throws IOException {
        final var service = new Dl7ReaderService(diveServiceReturningComputer());
        try (final InputStream inputStream =
                Dl7ReaderServiceTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            final var bytes =
                    Objects.requireNonNull(inputStream, "fixture not found").readAllBytes();
            return service.parse(user, FIXTURE, bytes);
        }
    }

    @Test
    void parsesTheRealFixtureWithoutThrowing() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles()).hasSize(1);
    }

    @Test
    void tagsTheImportAsDl7Shearwater() throws IOException {
        assertThat(parseFixture().source()).isEqualTo(PendingImportSource.DL7_SHEARWATER);
    }

    @Test
    void usesTheRealAnonymizedDeviceSerial() throws IOException {
        assertThat(parseFixture().computerSerial()).isEqualTo("1000000099");
    }

    @Test
    void startDateMatchesTheZdhSegmentTimestamp() throws IOException {
        assertThat(parseFixture().startDate()).isEqualTo(Instant.parse("2026-08-22T10:13:49Z"));
    }

    @Test
    void maxDepthMatchesTheZdtTrailerSegment() throws IOException {
        assertThat(parseFixture().maxDepth()).isEqualTo(42.7);
    }

    @Test
    void profileHasOneMeasurementPerProfileRow() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles().getFirst().measurements()).hasSize(942);
    }

    @Test
    void noMeasurementHasDecoOrTtsSinceNeitherColumnWasIdentifiable() throws IOException {
        final var result = parseFixture();
        assertThat(result.payload().profiles().getFirst().measurements())
                .allSatisfy(
                        m -> {
                            assertThat(m.deco()).isEmpty();
                            assertThat(m.timeToSurface()).isNull();
                            assertThat(m.ndl()).isNull();
                        });
    }

    @Test
    void temperatureIsPopulatedFromTheProfileRows() throws IOException {
        final var result = parseFixture();
        final var measurements = result.payload().profiles().getFirst().measurements();
        assertThat(measurements)
                .anyMatch(m -> m.temperature() != null && m.temperature().celsius() < 10);
    }
}

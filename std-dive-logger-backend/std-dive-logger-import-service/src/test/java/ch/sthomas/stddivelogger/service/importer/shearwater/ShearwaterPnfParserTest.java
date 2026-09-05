package ch.sthomas.stddivelogger.service.importer.shearwater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterPnfLog;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Layout-level tests for the Shearwater native (PNF) binary decoder, driven by synthetic logs built
 * in {@link ShearwaterPnfTestLogs} so they run everywhere. The complementary check against a real
 * device's logs - every header figure cross-referenced with Shearwater Cloud's own metadata for the
 * same dive - lives in {@link ShearwaterDbFixtureTest}, which needs the (gitignored) personal
 * database fixture.
 */
class ShearwaterPnfParserTest {
    private static final long START = 1_706_046_140L;

    private static ShearwaterPnfLog parse(final List<ShearwaterPnfTestLogs.Sample> samples) {
        return ShearwaterPnfParser.parse(
                ShearwaterPnfTestLogs.build(START, 400, 3.4, 14, 1, 5000, samples));
    }

    @Test
    void readsHeaderAndFooterFigures() {
        final var log = parse(List.of(ShearwaterPnfTestLogs.Sample.openCircuit(3.2, 28)));
        assertThat(log.start()).isEqualTo(Instant.ofEpochSecond(START));
        assertThat(log.diveTime()).isEqualTo(Duration.ofSeconds(400));
        assertThat(log.maxDepthMeters()).isEqualTo(3.4);
        assertThat(log.logVersion()).isEqualTo(14);
        assertThat(log.mode()).isEqualTo(ShearwaterPnfLog.DiveComputerMode.OC_TEC);
        assertThat(log.sampleInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(log.imperialUnits()).isFalse();
        assertThat(log.serialNumber()).isEqualTo(0xA3B6F031L);
        assertThat(log.model()).isEqualTo(3);
    }

    @Test
    void timesSamplesOneIntervalApart() {
        final var log =
                parse(
                        List.of(
                                ShearwaterPnfTestLogs.Sample.openCircuit(1.0, 28),
                                ShearwaterPnfTestLogs.Sample.openCircuit(2.0, 28),
                                ShearwaterPnfTestLogs.Sample.openCircuit(3.0, 28)));
        assertThat(log.samples())
                .extracting(s -> s.time().toSeconds())
                .containsExactly(5L, 10L, 15L);
        assertThat(log.samples()).extracting("depthMeters").containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void readsNdlWhenNotInDecoAndAStopWhenItIs() {
        final var ndl =
                new ShearwaterPnfTestLogs.Sample(18.0, 12, 0, 25, 3, 21, 0, 0.6, 0, 0.02, false);
        final var deco =
                new ShearwaterPnfTestLogs.Sample(40.0, 8, 6, 12, 21, 21, 35, 1.2, 0, 0.11, false);
        final var log = parse(List.of(ndl, deco));

        final var first = log.samples().getFirst();
        assertThat(first.inMandatoryDeco()).isFalse();
        assertThat(first.decoStopDepthMeters()).isZero();
        assertThat(first.stopOrNdlTime()).isEqualTo(Duration.ofMinutes(25));
        assertThat(first.timeToSurface()).isEqualTo(Duration.ofMinutes(3));

        final var second = log.samples().getLast();
        assertThat(second.inMandatoryDeco()).isTrue();
        assertThat(second.decoStopDepthMeters()).isEqualTo(6.0);
        assertThat(second.stopOrNdlTime()).isEqualTo(Duration.ofMinutes(12));
        assertThat(second.timeToSurface()).isEqualTo(Duration.ofMinutes(21));
        assertThat(second.o2Percent()).isEqualTo(21);
        assertThat(second.heliumPercent()).isEqualTo(35);
        assertThat(second.cns()).isEqualTo(0.11);
    }

    @Test
    void readsTheOpenCircuitStatusBit() {
        final var open =
                new ShearwaterPnfTestLogs.Sample(20.0, 15, 0, 30, 2, 21, 0, 0.6, 0, 0, false);
        final var closed =
                new ShearwaterPnfTestLogs.Sample(20.0, 15, 0, 30, 2, 21, 0, 1.3, 1.3, 0, true);
        final var log = parse(List.of(open, closed));
        assertThat(log.samples()).extracting("closedCircuit").containsExactly(false, true);
        // Setpoint only means anything on the closed loop, so it isn't reported off it.
        assertThat(log.samples().getFirst().setpoint()).isNull();
        assertThat(log.samples().getLast().setpoint()).isEqualTo(1.3);
    }

    @Test
    void unwrapsNegativeTemperatures() {
        // Sub-zero readings come back as a signed byte the device offset by 102, so -110 is -8 C.
        // A raw byte between -101 and -1 unwraps to a positive value, which can't be right for a
        // reading the device flagged as negative in the first place - those clamp to 0 rather than
        // reporting a bogus warm temperature.
        final var log =
                parse(
                        List.of(
                                new ShearwaterPnfTestLogs.Sample(
                                        5.0, -110, 0, 99, 0, 21, 0, 0.21, 0, 0, false),
                                new ShearwaterPnfTestLogs.Sample(
                                        5.0, -100, 0, 99, 0, 21, 0, 0.21, 0, 0, false)));
        assertThat(log.samples()).extracting("temperatureCelsius").containsExactly(-8.0, 0.0);
    }

    @Test
    void skipsUnknownRecordTypesAndTrailingPadding() {
        // A real log carries tissue-loading and flag-array records the parser has no use for, plus
        // zero padding up to a 128-byte page - none of which may become samples.
        final var raw =
                ShearwaterPnfTestLogs.build(
                        START,
                        400,
                        3.4,
                        14,
                        1,
                        5000,
                        List.of(ShearwaterPnfTestLogs.Sample.openCircuit(3.2, 28)));
        final var withNoise = new byte[raw.length + 2 * ShearwaterPnfTestLogs.RECORD_SIZE];
        System.arraycopy(raw, 0, withNoise, 0, raw.length);
        withNoise[raw.length] = 0x70; // float tissue block
        withNoise[raw.length + ShearwaterPnfTestLogs.RECORD_SIZE] = (byte) 0x80; // flag array
        assertThat(ShearwaterPnfParser.parse(withNoise).samples()).hasSize(1);
    }

    @Test
    void roundTripsThroughTheCompressedBlobFormat() {
        final var raw =
                ShearwaterPnfTestLogs.build(
                        START,
                        400,
                        3.4,
                        14,
                        1,
                        5000,
                        List.of(ShearwaterPnfTestLogs.Sample.openCircuit(3.2, 28)));
        final var log =
                ShearwaterPnfParser.parseCompressed(
                        ShearwaterPnfTestLogs.gzipWithLengthPrefix(raw));
        assertThat(log.samples()).hasSize(1);
        assertThat(log.maxDepthMeters()).isEqualTo(3.4);
    }

    @Test
    void rejectsABlobWhoseLengthPrefixDoesNotMatch() {
        final var blob =
                ShearwaterPnfTestLogs.gzipWithLengthPrefix(
                        ShearwaterPnfTestLogs.build(
                                START,
                                400,
                                3.4,
                                14,
                                1,
                                5000,
                                List.of(ShearwaterPnfTestLogs.Sample.openCircuit(3.2, 28))));
        blob[0] = (byte) (blob[0] + 1);
        assertThatThrownBy(() -> ShearwaterPnfParser.parseCompressed(blob))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("truncated");
    }

    @Test
    void rejectsSomethingThatIsNotAPnfLog() {
        assertThatThrownBy(() -> ShearwaterPnfParser.parse(new byte[256]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a native (PNF) dive log");
        assertThatThrownBy(() -> ShearwaterPnfParser.parseCompressed(new byte[] {1, 2, 3, 4, 5}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a gzipped native (PNF) log");
    }

    @Test
    void fallsBackToTheTenSecondDefaultWhenTheIntervalRecordIsMissing() {
        // Opening record 5 only exists from log version 9, and not on every firmware even then.
        final var raw =
                ShearwaterPnfTestLogs.build(
                        START,
                        400,
                        3.4,
                        7,
                        1,
                        5000,
                        List.of(ShearwaterPnfTestLogs.Sample.openCircuit(3.2, 28)));
        final var log = ShearwaterPnfParser.parse(raw);
        assertThat(log.sampleInterval()).isEqualTo(Duration.ofSeconds(10));
        // Before log version 8 the dive mode isn't in the header either.
        assertThat(log.mode()).isEqualTo(ShearwaterPnfLog.DiveComputerMode.OC_TEC);
    }
}

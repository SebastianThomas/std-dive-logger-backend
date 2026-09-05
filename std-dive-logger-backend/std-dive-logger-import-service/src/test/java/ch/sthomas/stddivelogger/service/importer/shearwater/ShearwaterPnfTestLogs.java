package ch.sthomas.stddivelogger.service.importer.shearwater;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Builds synthetic Shearwater PNF logs byte by byte, so the parser's expectations can be tested
 * without the (gitignored, personal) real database fixture - and so this file doubles as an
 * executable statement of the record layout {@link ShearwaterPnfParser} decodes.
 */
public final class ShearwaterPnfTestLogs {
    public static final int RECORD_SIZE = 32;
    private static final int PAGE_SIZE = 128;

    private ShearwaterPnfTestLogs() {}

    /** One sample's worth of the fields the parser reads. */
    public record Sample(
            double depthMeters,
            int temperatureCelsius,
            int stopDepthMeters,
            int stopOrNdlMinutes,
            int ttsMinutes,
            int o2Percent,
            int hePercent,
            double ppo2,
            double setpoint,
            double cns,
            boolean closedCircuit) {

        public static Sample openCircuit(final double depth, final int temperature) {
            return new Sample(depth, temperature, 0, 99, 0, 21, 0, 0.21, 0, 0, false);
        }
    }

    public static byte[] build(
            final long startEpochSeconds,
            final int diveTimeSeconds,
            final double maxDepthMeters,
            final int logVersion,
            final int diveModeCode,
            final int sampleIntervalMillis,
            final List<Sample> samples) {
        final var records = new ArrayList<byte[]>();

        final var opening0 = record(0x10);
        opening0[8] = 0; // metric
        putU32(opening0, 12, startEpochSeconds);
        records.add(opening0);

        final var opening1 = record(0x11);
        putU16(opening1, 16, 1013); // atmospheric pressure, mbar
        records.add(opening1);

        records.add(record(0x12));

        final var opening3 = record(0x13);
        putU16(opening3, 3, 1000); // water density, g/l
        records.add(opening3);

        final var opening4 = record(0x14);
        opening4[1] = (byte) diveModeCode;
        opening4[16] = (byte) logVersion;
        records.add(opening4);

        final var opening5 = record(0x15);
        putU16(opening5, 23, sampleIntervalMillis);
        records.add(opening5);

        for (final var sample : samples) {
            records.add(sampleRecord(sample));
        }

        final var closing0 = record(0x20);
        putU16(closing0, 4, (int) Math.round(maxDepthMeters * 10));
        putU24(closing0, 6, diveTimeSeconds);
        records.add(closing0);
        records.add(record(0x21));
        records.add(record(0x22));
        records.add(record(0x23));
        records.add(record(0x24));

        final var last = record(0xFF);
        putU32(last, 2, 0xA3B6F031L);
        last[13] = 0x03; // Petrel-family model code
        records.add(last);

        return concatAndPad(records);
    }

    public static byte[] gzipWithLengthPrefix(final byte[] raw) {
        final var compressed = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(raw);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        final var body = compressed.toByteArray();
        final var out = new byte[4 + body.length];
        out[0] = (byte) raw.length;
        out[1] = (byte) (raw.length >> 8);
        out[2] = (byte) (raw.length >> 16);
        out[3] = (byte) (raw.length >> 24);
        System.arraycopy(body, 0, out, 4, body.length);
        return out;
    }

    private static byte[] sampleRecord(final Sample sample) {
        final var record = record(0x01);
        putU16(record, 1, (int) Math.round(sample.depthMeters() * 10));
        putU16(record, 3, sample.stopDepthMeters());
        putU16(record, 5, sample.ttsMinutes());
        record[7] = (byte) Math.round(sample.ppo2() * 100);
        record[8] = (byte) sample.o2Percent();
        record[9] = (byte) sample.hePercent();
        record[10] = (byte) sample.stopOrNdlMinutes();
        record[12] = (byte) (sample.closedCircuit() ? 0x02 : 0x12);
        record[14] = (byte) sample.temperatureCelsius();
        record[19] = (byte) Math.round(sample.setpoint() * 100);
        record[23] = (byte) Math.round(sample.cns() * 100);
        return record;
    }

    private static byte[] record(final int type) {
        final var record = new byte[RECORD_SIZE];
        record[0] = (byte) type;
        return record;
    }

    /** Real logs are zero-padded out to a 128-byte page; the parser must skip that padding. */
    private static byte[] concatAndPad(final List<byte[]> records) {
        final var used = records.size() * RECORD_SIZE;
        final var total = (used + PAGE_SIZE - 1) / PAGE_SIZE * PAGE_SIZE;
        final var out = new byte[total];
        var offset = 0;
        for (final var record : records) {
            System.arraycopy(record, 0, out, offset, RECORD_SIZE);
            offset += RECORD_SIZE;
        }
        return out;
    }

    private static void putU16(final byte[] data, final int offset, final int value) {
        data[offset] = (byte) (value >> 8);
        data[offset + 1] = (byte) value;
    }

    private static void putU24(final byte[] data, final int offset, final int value) {
        data[offset] = (byte) (value >> 16);
        data[offset + 1] = (byte) (value >> 8);
        data[offset + 2] = (byte) value;
    }

    private static void putU32(final byte[] data, final int offset, final long value) {
        data[offset] = (byte) (value >> 24);
        data[offset + 1] = (byte) (value >> 16);
        data[offset + 2] = (byte) (value >> 8);
        data[offset + 3] = (byte) value;
    }
}

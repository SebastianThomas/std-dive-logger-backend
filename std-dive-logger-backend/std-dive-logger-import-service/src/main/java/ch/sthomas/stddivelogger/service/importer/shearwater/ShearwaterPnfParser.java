package ch.sthomas.stddivelogger.service.importer.shearwater;

import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterPnfLog;
import ch.sthomas.stddivelogger.model.importer.shearwater.ShearwaterPnfSample;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Decodes Shearwater's "Petrel Native Format" (PNF) binary dive log - the on-device log a Perdix /
 * Petrel / Teric writes, stored in Shearwater Cloud's {@code log_data.data_bytes_1} column (and in
 * the {@code .swlogzp} files the desktop app exports).
 *
 * <p>Layout is a flat stream of <b>32-byte big-endian records</b> whose first byte is the record
 * type; there is no separate header/footer region, the opening ({@code 0x10}-{@code 0x19}) and
 * closing ({@code 0x20}-{@code 0x29}) records are simply records in the stream, and the tail is
 * zero-padded up to a 128-byte page boundary. Record types this parser doesn't recognise are
 * skipped, which is what makes the format forward-compatible - real logs already carry several
 * (float tissue-loading blocks, per-sample flag arrays) that carry nothing this project models.
 *
 * <p>The field layout is public knowledge, documented by libdivecomputer's {@code
 * shearwater_predator_parser.c} (LGPL-2.1). This is an independent Java implementation written
 * against that description of the format, not a translation of its code, and it was verified
 * field-by-field against real data - see {@code ShearwaterPnfParserTest}, which checks every
 * decoded header figure against Shearwater Cloud's own metadata for the same dive.
 *
 * <p><b>Deliberately not decoded</b>, each for a concrete reason:
 *
 * <ul>
 *   <li><i>Per-sample tank pressure</i> (bytes 19/27, in units of 2 psi) - this project's {@code
 *       DiveMeasurement} has no tank-pressure field, and the start/end pressures it <i>can</i> use
 *       come from {@code dive_details.TankProfileData} instead, which is the diver's own entry
 *       rather than a transmitter reading.
 *   <li><i>Individual O2 sensor millivolts</i> (bytes 12/14/15 x per-cell calibration) - {@link
 *       ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2} has no per-cell fields, and
 *       the device's own averaged PPO2 (byte 6) is the value that maps onto it.
 *   <li><i>GNSS entry/exit fixes</i> (opening/closing record 9, logversion 17+) - never populated
 *       in any dive of the only real database available to verify against, so the decode could not
 *       be checked; a wrong dive-site coordinate is worse than none.
 * </ul>
 */
public final class ShearwaterPnfParser {
    /** Every record - sample, opening, closing - is this long. */
    private static final int RECORD_SIZE = 32;

    /** Opening/closing records are numbered 0-9 each. */
    private static final int NUM_INDEXED_RECORDS = 10;

    private static final int RECORD_ABSENT = -1;

    private static final int TYPE_DIVE_SAMPLE = 0x01;
    private static final int TYPE_FREEDIVE_SAMPLE = 0x02;
    private static final int TYPE_AVELO_SAMPLE = 0x03;
    private static final int TYPE_OPENING_0 = 0x10;
    private static final int TYPE_OPENING_9 = 0x19;
    private static final int TYPE_CLOSING_0 = 0x20;
    private static final int TYPE_CLOSING_9 = 0x29;
    private static final int TYPE_FINAL = 0xFF;

    /** A freedive record packs four 8-byte sub-samples into one 32-byte record. */
    private static final int FREEDIVE_SUB_SAMPLE_SIZE = 8;

    /** Bit 0x10 of a sample's status byte is set while the diver is on open circuit. */
    private static final int STATUS_OPEN_CIRCUIT = 0x10;

    private static final Duration DEFAULT_SAMPLE_INTERVAL = Duration.ofSeconds(10);
    private static final double FEET_PER_METER = 3.280839895;

    /** {@code data_bytes_1} is a little-endian uncompressed length followed by a gzip stream. */
    private static final int GZIP_LENGTH_PREFIX = 4;

    private ShearwaterPnfParser() {}

    /**
     * Decompresses and decodes a {@code log_data.data_bytes_1} blob. The 4-byte little-endian
     * length prefix is validated against what actually inflates - a mismatch means the blob is
     * truncated or not a PNF log at all.
     */
    public static ShearwaterPnfLog parseCompressed(final byte[] blob) {
        if (blob.length <= GZIP_LENGTH_PREFIX) {
            throw new IllegalArgumentException("Shearwater log data is empty or truncated.");
        }
        final var declaredLength =
                (blob[0] & 0xFF)
                        | ((blob[1] & 0xFF) << 8)
                        | ((blob[2] & 0xFF) << 16)
                        | ((long) (blob[3] & 0xFF) << 24);
        final byte[] raw;
        try (final var gzip =
                new GZIPInputStream(
                        new ByteArrayInputStream(
                                blob, GZIP_LENGTH_PREFIX, blob.length - GZIP_LENGTH_PREFIX))) {
            raw = gzip.readAllBytes();
        } catch (final IOException e) {
            throw new IllegalArgumentException(
                    "Shearwater log data is not a gzipped native (PNF) log.", e);
        }
        if (raw.length != declaredLength) {
            throw new IllegalArgumentException(
                    "Shearwater log data is truncated: expected "
                            + declaredLength
                            + " bytes, got "
                            + raw.length
                            + ".");
        }
        return parse(raw);
    }

    /** Decodes an already-decompressed PNF log. */
    public static ShearwaterPnfLog parse(final byte[] data) {
        // Indexed by record number, -1 = that record isn't in this log.
        final var opening = new int[NUM_INDEXED_RECORDS];
        final var closing = new int[NUM_INDEXED_RECORDS];
        Arrays.fill(opening, RECORD_ABSENT);
        Arrays.fill(closing, RECORD_ABSENT);
        var finalRecord = RECORD_ABSENT;
        for (var offset = 0; offset + RECORD_SIZE <= data.length; offset += RECORD_SIZE) {
            if (isBlank(data, offset)) {
                continue;
            }
            final var type = data[offset] & 0xFF;
            if (type >= TYPE_OPENING_0 && type <= TYPE_OPENING_9) {
                opening[type - TYPE_OPENING_0] = offset;
            } else if (type >= TYPE_CLOSING_0 && type <= TYPE_CLOSING_9) {
                closing[type - TYPE_CLOSING_0] = offset;
            } else if (type == TYPE_FINAL) {
                finalRecord = offset;
            }
        }
        // Records 0-4 carry everything the header/footer figures are read from. Anything past 4 is
        // firmware-dependent (record 5 is absent on older Petrels, see the sample-interval default
        // below), so only these five are required.
        for (var i = 0; i <= 4; i++) {
            if (opening[i] == RECORD_ABSENT || closing[i] == RECORD_ABSENT) {
                throw new IllegalArgumentException(
                        "Shearwater log is missing opening/closing record "
                                + i
                                + " - not a native (PNF) dive log.");
            }
        }
        final var opening0 = opening[0];
        final var closing0 = closing[0];
        final var imperial = data[opening0 + 8] != 0;
        final var logVersion = data[opening[4] + 16] & 0xFF;
        // The dive mode only became a header field in log version 8; before that the only signal is
        // the per-sample open-circuit status bit, so default to plain OC there.
        final var mode =
                logVersion >= 8
                        ? ShearwaterPnfLog.DiveComputerMode.fromCode(data[opening[4] + 1] & 0xFF)
                        : ShearwaterPnfLog.DiveComputerMode.OC_TEC;
        final var interval = sampleInterval(data, opening, logVersion);
        final var atmosphericMbar = u16(data, opening[1] + 16);
        final var densityGramsPerLiter = u16(data, opening[3] + 3);

        final var samples =
                readSamples(data, interval, imperial, atmosphericMbar, densityGramsPerLiter);

        final var maxDepthRaw = u16(data, closing0 + 4) / 10.0;
        return new ShearwaterPnfLog(
                Instant.ofEpochSecond(u32(data, opening0 + 12)),
                Duration.ofSeconds(u24(data, closing0 + 6)),
                imperial ? maxDepthRaw / FEET_PER_METER : maxDepthRaw,
                logVersion,
                mode,
                interval,
                imperial,
                finalRecord == RECORD_ABSENT ? null : u32(data, finalRecord + 2),
                finalRecord == RECORD_ABSENT ? null : data[finalRecord + 13] & 0xFF,
                samples);
    }

    /**
     * Sample interval in milliseconds, from opening record 5. That record only exists from log
     * version 9, and not at all on some Petrel firmwares even then - every such log seen sampled at
     * the device's long-standing 10s default, which is what the fallback assumes.
     */
    private static Duration sampleInterval(
            final byte[] data, final int[] opening, final int logVersion) {
        final var record = opening[5];
        if (logVersion < 9 || record == RECORD_ABSENT) {
            return DEFAULT_SAMPLE_INTERVAL;
        }
        final var millis = u16(data, record + 23);
        return millis > 0 ? Duration.ofMillis(millis) : DEFAULT_SAMPLE_INTERVAL;
    }

    private static List<ShearwaterPnfSample> readSamples(
            final byte[] data,
            final Duration interval,
            final boolean imperial,
            final int atmosphericMbar,
            final int densityGramsPerLiter) {
        final var samples = new ArrayList<ShearwaterPnfSample>();
        var time = Duration.ZERO;
        for (var offset = 0; offset + RECORD_SIZE <= data.length; offset += RECORD_SIZE) {
            if (isBlank(data, offset)) {
                continue;
            }
            final var type = data[offset] & 0xFF;
            if (type == TYPE_DIVE_SAMPLE || type == TYPE_AVELO_SAMPLE) {
                time = time.plus(interval);
                samples.add(diveSample(data, offset, time, imperial, type == TYPE_AVELO_SAMPLE));
            } else if (type == TYPE_FREEDIVE_SAMPLE) {
                for (var i = 0; i < RECORD_SIZE / FREEDIVE_SUB_SAMPLE_SIZE; i++) {
                    final var sub = offset + i * FREEDIVE_SUB_SAMPLE_SIZE;
                    // A partly-used freedive record is zero-padded, so the first blank sub-sample
                    // ends the record rather than adding a bogus surface sample.
                    if (isBlank(data, sub, FREEDIVE_SUB_SAMPLE_SIZE)) {
                        break;
                    }
                    time = time.plus(interval);
                    samples.add(
                            freediveSample(data, sub, time, atmosphericMbar, densityGramsPerLiter));
                }
            }
        }
        return List.copyOf(samples);
    }

    /**
     * Sample-record fields all sit one byte past their legacy-Predator offsets, because PNF puts
     * the record type in byte 0 - so every offset below is "documented offset + 1".
     */
    private static ShearwaterPnfSample diveSample(
            final byte[] data,
            final int offset,
            final Duration time,
            final boolean imperial,
            final boolean avelo) {
        final var rawDepth = u16(data, offset + 1) / 10.0;
        // An Avelo record has no status byte at all, so it can only ever be open circuit.
        final var status = avelo ? STATUS_OPEN_CIRCUIT : data[offset + 12] & 0xFF;
        final var closedCircuit = (status & STATUS_OPEN_CIRCUIT) == 0;
        final var rawStopDepth = u16(data, offset + 3);
        return new ShearwaterPnfSample(
                time,
                imperial ? rawDepth / FEET_PER_METER : rawDepth,
                temperatureCelsius(data[offset + 14], imperial),
                imperial ? rawStopDepth / FEET_PER_METER : rawStopDepth,
                Duration.ofMinutes(data[offset + 10] & 0xFF),
                Duration.ofMinutes(u16(data, offset + 5)),
                data[offset + 8] & 0xFF,
                data[offset + 9] & 0xFF,
                (data[offset + 7] & 0xFF) / 100.0,
                closedCircuit ? (data[offset + 19] & 0xFF) / 100.0 : null,
                (data[offset + 23] & 0xFF) / 100.0,
                closedCircuit);
    }

    /**
     * A freedive sub-sample stores absolute ambient pressure rather than depth, so the water column
     * has to be derived: {@code (ambient - surface) mbar} over {@code density x g}.
     */
    private static ShearwaterPnfSample freediveSample(
            final byte[] data,
            final int offset,
            final Duration time,
            final int atmosphericMbar,
            final int densityGramsPerLiter) {
        final var ambientMbar = u16(data, offset + 1);
        final var density = densityGramsPerLiter > 0 ? densityGramsPerLiter : 1025;
        final var depth = (ambientMbar - atmosphericMbar) * 100.0 / (density * 9.80665);
        return new ShearwaterPnfSample(
                time,
                depth,
                i16(data, offset + 3) / 10.0,
                0,
                Duration.ZERO,
                Duration.ZERO,
                0,
                0,
                null,
                null,
                null,
                false);
    }

    /**
     * Sub-zero readings arrive as a signed byte the device offset by 102, so -110 means -8 C. A raw
     * byte between -101 and -1 unwraps to a positive number, which can't be a genuine reading the
     * device flagged as negative - those clamp to 0 rather than reporting a bogus warm temperature.
     */
    private static double temperatureCelsius(final byte raw, final boolean imperial) {
        var temperature = (int) raw;
        if (temperature < 0) {
            temperature += 102;
            if (temperature > 0) {
                temperature = 0;
            }
        }
        return imperial ? (temperature - 32.0) * 5.0 / 9.0 : temperature;
    }

    private static boolean isBlank(final byte[] data, final int offset) {
        return isBlank(data, offset, RECORD_SIZE);
    }

    private static boolean isBlank(final byte[] data, final int offset, final int length) {
        for (var i = offset; i < offset + length; i++) {
            if (data[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static int u16(final byte[] data, final int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int i16(final byte[] data, final int offset) {
        return (short) u16(data, offset);
    }

    private static int u24(final byte[] data, final int offset) {
        return ((data[offset] & 0xFF) << 16)
                | ((data[offset + 1] & 0xFF) << 8)
                | (data[offset + 2] & 0xFF);
    }

    private static long u32(final byte[] data, final int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}

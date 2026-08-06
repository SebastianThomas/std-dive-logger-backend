package ch.sthomas.stddivelogger.service.importer.poseidon;

import ch.sthomas.stddivelogger.model.dive.Dive;
import ch.sthomas.stddivelogger.model.dive.gear.DiveComputer;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfile;
import ch.sthomas.stddivelogger.model.dive.profile.DiveProfileSummary;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurement;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.DiveMeasurementWithId;
import ch.sthomas.stddivelogger.model.dive.profile.measurement.PO2;
import ch.sthomas.stddivelogger.model.user.FrontendUser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class PosbParser {

    /* ==============================
    Public API
    ============================== */

    public Dive parse(
            final InputStream in,
            final long diveId,
            final FrontendUser user,
            final DiveComputer diveComputer,
            final int diveNumber)
            throws IOException {

        final var rawData = readAll(in);

        // Automatically detect header and sample start
        final var sampleBytes = extractSampleBytes(rawData);

        // Decode samples (depth + optional PO2)
        final var samples = decodeSamples(sampleBytes);
        if (samples.isEmpty()) {
            throw new IOException(
                    "No dive samples found. Maybe header length is wrong or file is uncompressed");
        }

        // Compute dive start & end
        final var start = Instant.now();
        final var end = start.plusMillis(samples.getLast().timeTenthSeconds * 100L);

        final var measurements = buildMeasurements(samples, start);
        final var summary = buildSummary(samples, start, end);
        final var profile = new DiveProfile(1L, diveComputer, start, end, measurements, summary);

        return new Dive(
                diveId,
                user,
                diveNumber,
                null, // notes
                null, // customIdentifier
                null, // previewImage
                null, // visibility
                null, // gasConsumption
                null, // configuration
                null, // site
                List.of(profile),
                null,
                List.of(), // buddies
                null);
    }

    /* ==============================
    Internal sample model
    ============================== */

    private record Sample(int timeTenthSeconds, double depth, Double po2) {}

    /* ==============================
    Step 1: locate & decompress or raw
    ============================== */

    private static byte[] extractSampleBytes(final byte[] data) throws IOException {
        // Guess header length (usually 12-16 bytes)
        final var headerLenCandidates = new int[] {12, 16, 24};
        for (final var headerLen : headerLenCandidates) {
            if (headerLen >= data.length) continue;
            final var candidate = Arrays.copyOfRange(data, headerLen, data.length);

            // Try DEFLATE (raw/zlib)
            final var inflatedRaw = tryDeflate(candidate, true);
            if (inflatedRaw != null && inflatedRaw.length >= 128) return inflatedRaw;

            final var inflatedZlib = tryDeflate(candidate, false);
            if (inflatedZlib != null && inflatedZlib.length >= 128) return inflatedZlib;

            // If not compressed, maybe candidate is already raw floats
            if (candidate.length >= 128) return candidate;
        }

        throw new IOException("Unable to locate POSB dive samples in file");
    }

    private static byte[] tryDeflate(final byte[] data, final boolean raw) {
        try (final var out = new ByteArrayOutputStream();
                final var inflater = new Inflater(raw)) {
            inflater.setInput(data);
            final var buf = new byte[8192];
            while (!inflater.finished() && !inflater.needsInput()) {
                final var n = inflater.inflate(buf);
                if (n == 0) break;
                out.write(buf, 0, n);
            }
            inflater.end();
            return out.size() < 128 ? null : out.toByteArray();
        } catch (final DataFormatException | IOException ignored) {
            return null;
        }
    }

    /* ==============================
    Step 2: decode records
    ============================== */

    private static List<Sample> decodeSamples(final byte[] raw) {
        final var recordSize = detectRecordSize(raw);
        final var out = new ArrayList<Sample>();
        var t = 0;
        double depth = 0; // start at 0

        for (var off = 0; off + recordSize <= raw.length; off += recordSize) {
            // Read depth as u16 (decimeters)
            final var rawDepth = i16(raw, off);
            depth += rawDepth / 10000.0;

            if (depth < -1 || depth > 400) continue;

            Double po2 = null;
            if (recordSize >= 8 && off + 4 < raw.length) {
                final var rawPo2 = u16(raw, off + 4);
                po2 = rawPo2 / 100.0; // scale depending on firmware (0.0-2.5)
            }

            out.add(new Sample(t, depth, po2));
            t++;
        }
        return out;
    }

    private static int detectRecordSize(final byte[] d) {
        for (final var s : new int[] {8, 4}) { // prefer depth+po2
            if (d.length % s == 0) return s;
        }
        return 4; // fallback
    }

    /* ==============================
    Step 3: build measurements
    ============================== */

    private static List<DiveMeasurementWithId> buildMeasurements(
            final List<Sample> samples, final Instant start) {
        final var out = new ArrayList<DiveMeasurementWithId>();
        long id = 1;

        for (final var s : samples) {
            final var time = start.plusMillis(s.timeTenthSeconds * 100L);
            final var m =
                    new DiveMeasurement(
                            time,
                            null, // temperature
                            s.depth,
                            null, // ndl
                            List.of(), // deco
                            null, // gas
                            s.po2 != null ? new PO2(s.po2, null, null) : null,
                            null, // rmv
                            null, // n2
                            null, // o2tox
                            null // cns
                            );
            out.add(new DiveMeasurementWithId(m, id));
            id++;
        }

        return out;
    }

    /* ==============================
    Step 4: build summary
    ============================== */

    private static DiveProfileSummary buildSummary(
            final List<Sample> samples, final Instant start, final Instant end) {
        final var max = samples.stream().mapToDouble(Sample::depth).max().orElse(0);
        final var avg = samples.stream().mapToDouble(Sample::depth).average().orElse(0);
        final var bottomTime = Duration.between(start, end);

        return new DiveProfileSummary(
                start,
                end,
                avg,
                max,
                null, // surface interval
                bottomTime,
                null, // descent time
                null, // ascent time
                null, // avg ascent rate
                null,
                null,
                null,
                null,
                null);
    }

    /* ==============================
    Binary helpers
    ============================== */

    private static byte[] readAll(final InputStream in) throws IOException {
        try (final var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private static int u16(final byte[] b, final int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private static int i16(final byte[] b, final int o) {
        return (short) ((b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8));
    }

    private static long u32(final byte[] b, final int o) {
        return ((long) b[o] & 0xff)
                | (((long) b[o + 1] & 0xff) << 8)
                | (((long) b[o + 2] & 0xff) << 16)
                | (((long) b[o + 3] & 0xff) << 24);
    }

    private static float f32(final byte[] b, final int o) {
        return Float.intBitsToFloat((int) u32(b, o));
    }
}

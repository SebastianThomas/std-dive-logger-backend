package ch.sthomas.stddivelogger.service.importer.poseidon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PosbParserFixed {

    // Record type for clean data modeling (Modern Java 16+)
    public record DiveSample(int second, double depthMeters, double tempCelsius, double ppo2Bar) {}

    public record PosbFile(String signature, int diveId, List<DiveSample> samples) {}

    private static final int HEADER_SIZE = 256;
    private static final int PACKET_SIZE = 32;

    public static void parse(final InputStream inputStream) throws IOException {
        final var diveLog = parsePosbContent(inputStream);

        System.out.printf("--- Dive Metadata ---%n");
        System.out.printf("Format: %s%n", diveLog.signature());
        System.out.printf("Dive ID: %d%n", diveLog.diveId());

        System.out.printf("%n--- First 15 Samples ---%n");
        System.out.printf(
                "%-10s | %-10s | %-8s | %-8s%n", "Time (s)", "Depth (m)", "ppO2", "Temp (C)");
        System.out.println("-".repeat(45));

        diveLog.samples().stream()
                .limit(15) // Modern Stream API
                .forEach(
                        s ->
                                System.out.printf(
                                        "%-10d | %-10.2f | %-8.2f | %-8.1f%n",
                                        s.second(), s.depthMeters(), s.ppo2Bar(), s.tempCelsius()));
    }

    public static PosbFile parsePosbContent(final InputStream stream) throws IOException {
        // try-with-resources and modern Files API
        final var data = stream.readAllBytes();

        // Use ByteBuffer for easier multi-byte reading with Little Endian order
        final var buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Parse Header
        final var sigBytes = new byte[8];
        buffer.get(sigBytes);
        final var signature = new String(sigBytes);

        // Dive ID is at offset 12
        final var diveId = buffer.getShort(12) & 0xFFFF;

        // 2. Parse Samples
        final List<DiveSample> samples = new ArrayList<>();
        var currentOffset = HEADER_SIZE;

        var second = 1;
        while (currentOffset + PACKET_SIZE <= data.length) {
            // Depth: Float at offset 0 of packet
            // final var depth = buffer.getFloat(currentOffset);

            // Temp: Float at offset 4 of packet
            // final var temp = buffer.getFloat(currentOffset + 4);

            // ppO2: Uint16 at offset 12 (millibar)
            // final var ppo2Mbar = buffer.getShort(currentOffset + 12) & 0xFFFF;
            // final var ppo2Bar = ppo2Mbar / 1000.0;

            final int rawPressure = buffer.getInt(currentOffset); // 4 bytes
            final int rawTemp = buffer.getShort(currentOffset + 4); // 2 bytes
            final int rawPpo2 = buffer.getShort(currentOffset + 12) & 0xFFFF; // 2 bytes

            double depth = (rawPressure - 1013.0) / 100.0; // Subtract surface pressure
            double temp = rawTemp / 100.0;
            double ppo2Bar = rawPpo2 / 1000.0;

            samples.add(new DiveSample(second++, depth, temp, ppo2Bar));
            currentOffset += PACKET_SIZE;
        }

        return new PosbFile(signature, diveId, samples);
    }
}

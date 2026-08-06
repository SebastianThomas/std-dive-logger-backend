package ch.sthomas.stddivelogger.service.importer.poseidon;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

class PosbParserTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "m28-2091_dive_0011.posb",
                "m28-2091_dive_0012.posb",
                "m28-2091_dive_0013.posb",
            })
    void parse(final String filename) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            final var dive = new PosbParser().parse(inputStream, 0, null, null, 0);
            System.out.println(dive);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "m28-2091_dive_0011.posb",
                "m28-2091_dive_0012.posb",
                "m28-2091_dive_0013.posb",
            })
    void parseFixed(final String filename) throws IOException {
        try (final var inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
            PosbParserFixed.parse(inputStream);
        }
    }
}

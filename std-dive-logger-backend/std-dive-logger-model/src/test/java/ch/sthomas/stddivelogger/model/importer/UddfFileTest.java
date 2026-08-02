package ch.sthomas.stddivelogger.model.importer;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.model.dive.DiveNumber;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class UddfFileTest {

    private static UddfFile.UddfProfileDataDive diveWithNumber(final String divenumber) {
        final var infoBeforeDive =
                new UddfFile.UddfInfoBeforeDive(
                        List.of(), divenumber, Instant.EPOCH, 0, null, null, 0);
        return new UddfFile.UddfProfileDataDive(
                "dive-1", null, infoBeforeDive, null, new UddfFile.UddfSamples(List.of()), null);
    }

    @Test
    void plainDiveNumberIsNotFractional() {
        final var result = UddfFile.diveNumber(diveWithNumber("42"));
        assertThat(result).contains(new DiveNumber(42, 0));
    }

    @Test
    void plusPrefixedDiveNumberIsFractional() {
        final var result = UddfFile.diveNumber(diveWithNumber("+42"));
        assertThat(result).contains(new DiveNumber(42, 1));
    }

    @Test
    void minusPrefixedDiveNumberIsFractionalAndDoesNotThrow() {
        // Shearwater's UDDF export marks a dive's OC-bailout/CC companion profile with either a
        // "+" or "-" prefix - a bare Integer.parseInt("-42") would otherwise succeed and hand a
        // negative number to DiveNumber's compact constructor, which rejects it.
        final var result = UddfFile.diveNumber(diveWithNumber("-42"));
        assertThat(result).contains(new DiveNumber(42, 1));
    }

    @Test
    void dottedDiveNumberIsFractional() {
        final var result = UddfFile.diveNumber(diveWithNumber("42.01"));
        assertThat(result).contains(new DiveNumber(42, 1));
    }

    @Test
    void blankDiveNumberIsEmpty() {
        assertThat(UddfFile.diveNumber(diveWithNumber(""))).isEmpty();
    }

    @Test
    void nullDiveNumberIsEmpty() {
        assertThat(UddfFile.diveNumber(diveWithNumber(null))).isEmpty();
    }

    @Test
    void garbageDiveNumberIsEmptyRatherThanThrowing() {
        assertThat(UddfFile.diveNumber(diveWithNumber("abc"))).isEmpty();
    }

    @Test
    void bareSignWithNoDigitsIsEmptyRatherThanThrowing() {
        assertThat(UddfFile.diveNumber(diveWithNumber("+"))).isEmpty();
        assertThat(UddfFile.diveNumber(diveWithNumber("-"))).isEmpty();
    }

    @Test
    void zeroDiveNumberIsEmptyRatherThanThrowing() {
        // DiveNumber's compact constructor rejects <= 0, which is an IllegalArgumentException, not
        // a NumberFormatException - this only stays graceful if that's caught too.
        assertThat(UddfFile.diveNumber(diveWithNumber("0"))).isEmpty();
    }

    @Test
    void malformedDottedDiveNumberIsEmptyRatherThanThrowing() {
        assertThat(UddfFile.diveNumber(diveWithNumber("42."))).isEmpty();
        assertThat(UddfFile.diveNumber(diveWithNumber("abc.def"))).isEmpty();
    }
}

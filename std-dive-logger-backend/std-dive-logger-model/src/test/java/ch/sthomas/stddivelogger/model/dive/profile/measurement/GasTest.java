package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Direct unit coverage for {@link Gas}'s compact constructor - both the original "must sum to 100%"
 * check and the negative-component check added alongside it (see that check's own comment for why
 * the sum check alone isn't enough for gas mixes built via the 2-arg convenience constructor, where
 * N2 is always computed as {@code 1 - o2 - he}).
 */
class GasTest {

    @Test
    void airIsAValidPureFractionNotARawPercent() {
        // Gas.AIR used to be `new Gas(20.9)` (a raw percent, not a 0-1 fraction) - this only ever
        // "worked" because the sum check was tautologically satisfied for any o2 value from the
        // 2-arg constructor. Pin the actual, correct value down explicitly.
        assertThat(Gas.AIR.o2()).isEqualTo(0.209);
        assertThat(Gas.AIR.n2()).isCloseTo(0.791, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void aValidTrimixMixIsAccepted() {
        final var trimix = new Gas(0.21, 0.35);
        assertThat(trimix.o2()).isEqualTo(0.21);
        assertThat(trimix.he()).isEqualTo(0.35);
        assertThat(trimix.n2()).isCloseTo(0.44, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void pureO2IsAccepted() {
        final var pureO2 = new Gas(1.0, 0.0);
        assertThat(pureO2.o2()).isEqualTo(1.0);
        assertThat(pureO2.n2()).isEqualTo(0.0);
    }

    @Test
    void componentsNotSummingToOneAreRejected() {
        assertThatThrownBy(() -> new Gas(0.21, 0.0, 0.0, 0.0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Gas must consist of 100%");
    }

    @Test
    void o2PlusHeOverOneHundredPercentIsRejected() {
        // Via the 2-arg constructor, n2 = 1 - o2 - he = 1 - 0.8 - 0.5 = -0.3 - the sum is still
        // exactly 1 (0.8 + -0.3 + 0.5 + 0), so only the negative-component check catches this.
        assertThatThrownBy(() -> new Gas(0.8, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void negativeN2DirectlyIsRejectedEvenWhenSumIsExactlyOne() {
        assertThatThrownBy(() -> new Gas(0.8, -0.3, 0.5, 0.0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }
}

package ch.sthomas.stddivegastracker.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivegastracker.calc.gas.GasMix;
import ch.sthomas.stddivegastracker.model.fill.StageResult;
import ch.sthomas.stddivegastracker.model.fill.StageSource;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

/** Wire conventions from DOMAIN §3 / API.md "Conventions". */
final class JacksonConfigTest {

    record Sample(BigDecimal amount, Instant at) {}

    private final tools.jackson.databind.json.JsonMapper mapper = new JacksonConfig().jsonMapper();

    @Test
    void moneyIsAPlainDecimalStringAndInstantsAreEpochMillis() {
        final var json =
                mapper.writeValueAsString(
                        new Sample(new BigDecimal("1E+3").setScale(2), Instant.ofEpochMilli(1234)));
        assertThat(json).isEqualTo("{\"amount\":\"1000.00\",\"at\":1234}");
    }

    @Test
    void moneyIsReadFromStringsAndNumbers() {
        assertThat(mapper.readValue("{\"amount\":\"12.30\",\"at\":0}", Sample.class).amount())
                .isEqualByComparingTo("12.30");
        assertThat(mapper.readValue("{\"amount\":12.3,\"at\":0}", Sample.class).amount())
                .isEqualByComparingTo("12.30");
    }

    @Test
    void aNullFieldIsLeftOutNotSentAsNull() {
        final var air =
                new StageResult(
                        2,
                        new StageSource.Air(),
                        GasMix.AIR,
                        200,
                        1,
                        0.2,
                        0,
                        0,
                        0,
                        new BigDecimal("0.00"),
                        false,
                        null);
        assertThat(mapper.writeValueAsString(air)).doesNotContain("sourcePressureAfterBar");
    }
}

package ch.sthomas.stddivelogger.model.dive.profile.measurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.exc.ValueInstantiationException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Regression coverage for the "Gas must consist of 100%" 400 the dive-edit cylinder editor used to
 * trigger on every save: {@link Gas}'s canonical (all-args) constructor is what Jackson uses to
 * deserialize a JSON object with no {@code @JsonCreator} to disambiguate, so a client that only
 * sends {@code o2}/{@code he} (as the cylinder editor's {@code gas: {o2, he}} does - {@code n2} is
 * implied, not tracked separately) leaves {@code n2}/{@code h2} defaulted to 0.0, failing the
 * compact constructor's 100%-sum check for anything but pure O2. The fix is on the frontend
 * (DiveEditView.vue's submit payload now fills in {@code n2} before sending), not here - this just
 * proves the failure mode and the fix's shape are both real.
 */
class GasJsonDeserializationTest {

    private static final JsonMapper mapper = ObjectMapperUtils.objectMapperBuilder(b -> {}).build();

    @Test
    void aPartialGasObjectWithNoN2IsRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"o2\":0.21,\"he\":0}", Gas.class))
                .isInstanceOf(ValueInstantiationException.class)
                .hasMessageContaining("Gas must consist of 100%");
    }

    @Test
    void aFullGasObjectWithN2FilledInIsAccepted() {
        final var gas = mapper.readValue("{\"o2\":0.21,\"n2\":0.79,\"he\":0,\"h2\":0}", Gas.class);
        assertThat(gas.o2()).isEqualTo(0.21);
        assertThat(gas.n2()).isEqualTo(0.79);
    }

    @Test
    void aPureO2GasObjectIsAccepted() {
        final var gas = mapper.readValue("{\"o2\":1,\"n2\":0,\"he\":0,\"h2\":0}", Gas.class);
        assertThat(gas.o2()).isEqualTo(1.0);
    }
}

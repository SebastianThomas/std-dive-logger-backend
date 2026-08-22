package ch.sthomas.stddivelogger.model.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Direct unit coverage for {@link DBResult}'s compact constructor - found while auditing this
 * project's other records for the same class of gap {@code Gas}'s validation had (see {@code
 * GasTest}): the pre-existing check only rejected "both value and exception set", leaving "neither
 * set" unguarded, which would have made {@code isException() == false} while {@code value()} still
 * silently returned {@code null} instead of throwing.
 */
class DBResultTest {

    @Test
    void aValueOnlyResultIsNotAnException() {
        final var result = new DBResult<>("ok");
        assertThat(result.isException()).isFalse();
        assertThat(result.value()).isEqualTo("ok");
    }

    @Test
    void anExceptionOnlyResultIsAnException() {
        final var exception = new DiveDBConstraintException("duplicate", new RuntimeException());
        final var result = new DBResult<String>(null, exception);
        assertThat(result.isException()).isTrue();
        assertThat(result.dbException()).isSameAs(exception);
    }

    @Test
    void bothValueAndExceptionSetIsRejected() {
        final var exception = new DiveDBConstraintException("duplicate", new RuntimeException());
        assertThatThrownBy(() -> new DBResult<>("ok", exception))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be null");
    }

    @Test
    void neitherValueNorExceptionSetIsRejected() {
        assertThatThrownBy(() -> new DBResult<String>(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be set");
    }
}

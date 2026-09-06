package ch.sthomas.stddivelogger.model.dive.profile;

import static org.assertj.core.api.Assertions.assertThat;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class DurationJsonTest {
    @Test
    void durationsAreMillisecondsInBothDirections() {
        final var mapper = ObjectMapperUtils.objectMapperBuilder(b -> {}).build();
        for (final var ms : new long[] {0, 1500, -250, 90061000}) {
            final var duration = Duration.ofMillis(ms);
            assertThat(mapper.writeValueAsString(duration)).isEqualTo(Long.toString(ms));
            assertThat(mapper.readValue(Long.toString(ms), Duration.class)).isEqualTo(duration);
        }
    }
}

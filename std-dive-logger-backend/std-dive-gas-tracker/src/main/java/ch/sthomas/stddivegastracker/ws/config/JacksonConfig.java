package ch.sthomas.stddivegastracker.ws.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;

/**
 * Same wire conventions as the dive logger (Instant = epoch millis), plus: every {@link BigDecimal}
 * is money and goes out as a plain decimal string ({@code "12.30"}, DOMAIN §3), and a null field is
 * left out - the OpenAPI document (and the frontend's generated types) call it optional, not null.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .changeDefaultPropertyInclusion(
                        inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
                .addModule(
                        new SimpleModule("money")
                                .addSerializer(BigDecimal.class, new MoneySerializer()))
                .build();
    }

    static final class MoneySerializer extends StdSerializer<BigDecimal> {
        MoneySerializer() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(
                final BigDecimal value,
                final JsonGenerator generator,
                final SerializationContext context) {
            generator.writeString(value.toPlainString());
        }
    }
}

package ch.sthomas.stddivelogger.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ObjectMapperUtils {
    public static JsonMapper.Builder objectMapperBuilder(
            final Consumer<JsonMapper.Builder> customizer) {
        final var jsonMapperBuilder =
                JsonMapper.builder()
                        // support for LocalDate to String conversion in ISO yyyy-MM-dd format
                        .addModule(javaTimeModule())
                        // ParanamerModule allows using @JsonCreator without needing @JsonProperty
                        .addModule(new ParanamerModule())
                        // Jdk8Module supports Optional
                        .addModule(new Jdk8Module())
                        .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                        .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                        .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                        .enable(
                                DeserializationFeature
                                        .READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

        customizer.accept(jsonMapperBuilder);

        return jsonMapperBuilder;
    }

    public static JavaTimeModule javaTimeModule() {
        final var javaTimeModule = new JavaTimeModule();
        final var localDateSerializer =
                new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        javaTimeModule.addSerializer(LocalDate.class, localDateSerializer);
        return javaTimeModule;
    }
}

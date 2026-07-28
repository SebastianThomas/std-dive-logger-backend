package ch.sthomas.stddivelogger.utils;

import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.converter.json.ProblemDetailJacksonXmlMixin;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.xml.XmlMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class ObjectMapperUtils {
    public static JsonMapper.Builder objectMapperBuilder(
            final Consumer<JsonMapper.Builder> customizer) {
        final var jsonMapperBuilder =
                JsonMapper.builder()
                        // support for LocalDate to String conversion in ISO yyyy-MM-dd format
                        .addModule(localDateModule())
                        .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                        .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                        .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                        .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                        .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);

        customizer.accept(jsonMapperBuilder);

        return jsonMapperBuilder;
    }

    public static XmlMapper.Builder xmlMapperBuilder(
            final Consumer<XmlMapper.Builder> customizer) {
        final var xmlMapperBuilder =
                XmlMapper.builder()
                        // support for LocalDate to String conversion in ISO yyyy-MM-dd format
                        .addModule(localDateModule())
                        .addMixIn(ProblemDetail.class, ProblemDetailJacksonXmlMixin.class);

        customizer.accept(xmlMapperBuilder);

        return xmlMapperBuilder;
    }

    public static SimpleModule localDateModule() {
        final var module = new SimpleModule();
        final var localDateSerializer =
                new LocalDateSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        module.addSerializer(LocalDate.class, localDateSerializer);
        return module;
    }
}

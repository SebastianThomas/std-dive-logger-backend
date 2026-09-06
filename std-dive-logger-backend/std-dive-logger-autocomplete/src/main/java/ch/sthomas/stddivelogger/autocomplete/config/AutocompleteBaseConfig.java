package ch.sthomas.stddivelogger.autocomplete.config;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class AutocompleteBaseConfig {

    static {
        SpringDocConfig.setDefaultConfigWith(_ -> {});
    }

    @Bean
    JsonMapper objectMapper() {
        final var geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        return ObjectMapperUtils.objectMapperBuilder(
                        customizer -> customizer.addModule(new JtsModule(geometryFactory)))
                .build();
    }
}

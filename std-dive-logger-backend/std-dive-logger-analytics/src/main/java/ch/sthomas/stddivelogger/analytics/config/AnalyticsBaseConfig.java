package ch.sthomas.stddivelogger.analytics.config;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import tools.jackson.databind.json.JsonMapper;

@Configuration
@EntityScan("ch.sthomas.stddivelogger.model.entity")
@EnableJpaRepositories("ch.sthomas.stddivelogger.data.repository")
@EnableTransactionManagement
public class AnalyticsBaseConfig {

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

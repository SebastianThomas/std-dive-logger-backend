package ch.sthomas.stddivelogger.importws.config;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

@Configuration
@EntityScan("ch.sthomas.stddivelogger.model.entity")
@EnableJpaRepositories("ch.sthomas.stddivelogger.data.repository")
@EnableTransactionManagement
@EnableRetry
public class ImportWsBaseConfig {

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

    @Bean
    XmlMapper xmlMapper() {
        return ObjectMapperUtils.xmlMapperBuilder(customizer -> {}).build();
    }
}

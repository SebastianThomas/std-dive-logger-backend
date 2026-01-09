package ch.sthomas.stddivelogger.importws.config;

import ch.sthomas.stddivelogger.utils.ObjectMapperUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.n52.jackson.datatype.jts.JtsModule;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@Configuration
@EntityScan("ch.sthomas.stddivelogger.model.entity")
@EnableJpaRepositories("ch.sthomas.stddivelogger.data.repository")
@EnableTransactionManagement
public class ImportWsBaseConfig {

    static {
        SpringDocConfig.setDefaultConfigWith(_ -> {});
    }

    @Bean
    ObjectMapper objectMapper() {
        final var geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        return ObjectMapperUtils.objectMapperBuilder(
                        customizer ->
                                customizer
                                        .addModule(new ProblemModule())
                                        .addModule(new ConstraintViolationProblemModule())
                                        .addModule(new JtsModule(geometryFactory)))
                .build();
    }

    @Bean
    XmlMapper xmlMapper() {
        final var xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new Jdk8Module());
        xmlMapper.registerModule(new ProblemModule());
        xmlMapper.registerModule(new ConstraintViolationProblemModule());
        xmlMapper.registerModule(new JavaTimeModule());
        return xmlMapper;
    }
}

package ch.sthomas.stddivelogger.data.config;

import com.zaxxer.hikari.HikariDataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** Independent maps-schema connection and migration lifecycle. */
@Configuration(proxyBeanMethods = false)
public class MapsDataSourceConfiguration {

    @Bean(name = "mapsDataSourceProperties", defaultCandidate = false)
    @ConfigurationProperties("maps.datasource")
    DataSourceProperties mapsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mapsDataSource", destroyMethod = "close", defaultCandidate = false)
    @ConfigurationProperties("maps.datasource.hikari")
    HikariDataSource mapsDataSource(
            @Qualifier("mapsDataSourceProperties") final DataSourceProperties properties,
            @Qualifier("dataSource") final DataSource primaryDataSource) {
        if (properties.getUrl() == null && primaryDataSource instanceof HikariDataSource primary) {
            properties.setUrl(primary.getJdbcUrl());
            properties.setUsername(primary.getUsername());
            properties.setPassword(primary.getPassword());
        }
        final HikariDataSource dataSource =
                properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        dataSource.setPoolName("maps-db-pool");
        dataSource.setMaximumPoolSize(2);
        dataSource.setMinimumIdle(0);
        return dataSource;
    }

    @Bean(name = "mapsJdbcTemplate", defaultCandidate = false)
    JdbcTemplate mapsJdbcTemplate(@Qualifier("mapsDataSource") final DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "mapsNamedParameterJdbcTemplate", defaultCandidate = false)
    NamedParameterJdbcTemplate mapsNamedParameterJdbcTemplate(
            @Qualifier("mapsDataSource") final DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "mapsTransactionManager", defaultCandidate = false)
    PlatformTransactionManager mapsTransactionManager(
            @Qualifier("mapsDataSource") final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "mapsFlyway", defaultCandidate = false)
    Flyway mapsFlyway(@Qualifier("mapsDataSource") final DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("maps")
                .defaultSchema("maps")
                .createSchemas(true)
                .locations("classpath:db/migration/maps")
                .table("flyway_schema_history")
                .load();
    }

    @Bean(name = "mapsFlywayMigrationInitializer", defaultCandidate = false)
    @DependsOn("flywayInitializer")
    FlywayMigrationInitializer mapsFlywayMigrationInitializer(
            @Qualifier("mapsFlyway") final Flyway mapsFlyway) {
        return new FlywayMigrationInitializer(mapsFlyway);
    }
}

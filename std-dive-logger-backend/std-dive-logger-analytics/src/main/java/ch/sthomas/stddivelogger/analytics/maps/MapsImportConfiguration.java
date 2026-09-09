package ch.sthomas.stddivelogger.analytics.maps;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MapsImportProperties.class)
public class MapsImportConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "maps.import.kubernetes.enabled", havingValue = "true")
    KubernetesClient mapsKubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}

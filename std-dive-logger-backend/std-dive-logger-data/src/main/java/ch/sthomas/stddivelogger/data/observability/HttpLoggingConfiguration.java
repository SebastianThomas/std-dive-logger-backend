package ch.sthomas.stddivelogger.data.observability;

import com.google.common.base.Suppliers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.zalando.logbook.PathFilter;
import org.zalando.logbook.Sink;
import org.zalando.logbook.Strategy;

@Configuration(proxyBeanMethods = false)
public class HttpLoggingConfiguration {
    @Bean
    Strategy safeBodyStrategy() {
        return new SafeBodyStrategy();
    }

    @Bean
    Sink requestLogSink() {
        return new RequestLogSink();
    }

    @Bean
    PathFilter routePathFilter(final ObjectProvider<RequestMappingHandlerMapping> mappings) {
        final var patterns =
                Suppliers.memoize(
                        () ->
                                mappings.orderedStream()
                                        .flatMap(
                                                mapping ->
                                                        mapping
                                                                .getHandlerMethods()
                                                                .keySet()
                                                                .stream())
                                        .flatMap(info -> info.getPatternValues().stream())
                                        .distinct()
                                        .map(PathPatternParser.defaultInstance::parse)
                                        .sorted(PathPattern.SPECIFICITY_COMPARATOR)
                                        .toList());
        return path -> {
            try {
                final var container = PathContainer.parsePath(path);
                return patterns.get().stream()
                        .filter(pattern -> pattern.matches(container))
                        .map(PathPattern::getPatternString)
                        .findFirst()
                        .orElse("UNKNOWN");
            } catch (IllegalArgumentException ignored) {
                return "UNKNOWN";
            }
        };
    }
}

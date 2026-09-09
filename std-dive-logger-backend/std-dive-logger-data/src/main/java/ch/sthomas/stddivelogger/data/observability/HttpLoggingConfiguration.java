package ch.sthomas.stddivelogger.data.observability;

import com.google.common.base.Suppliers;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.zalando.logbook.BodyFilter;
import org.zalando.logbook.HeaderFilter;
import org.zalando.logbook.PathFilter;
import org.zalando.logbook.Sink;
import org.zalando.logbook.Strategy;
import org.zalando.logbook.core.BodyFilters;
import org.zalando.logbook.core.CompositeSink;
import org.zalando.logbook.core.DefaultHttpLogWriter;
import org.zalando.logbook.core.DefaultSink;
import org.zalando.logbook.core.HeaderFilters;
import org.zalando.logbook.core.SplunkHttpLogFormatter;
import org.zalando.logbook.json.JsonBodyFilters;

import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class HttpLoggingConfiguration {
    @Bean
    Strategy safeBodyStrategy() {
        return new SafeBodyStrategy();
    }

    /**
     * A terse structured access record ({@link RequestLogSink}) is retained for client/server
     * failures, while Logbook's full per-request dump is written only when the {@code
     * org.zalando.logbook} logger is at TRACE.
     */
    @Bean
    Sink requestLogSink() {
        return new CompositeSink(
                List.of(
                        new RequestLogSink(),
                        new DefaultSink(new SplunkHttpLogFormatter(), new DefaultHttpLogWriter())));
    }

    /** Logbook already masks {@code Authorization}; also mask the session / refresh cookie. */
    @Bean
    HeaderFilter sensitiveHeaderFilter() {
        return HeaderFilter.merge(
                HeaderFilters.defaultValue(),
                HeaderFilters.replaceHeaders(Set.of("cookie", "set-cookie"), "XXX"));
    }

    /** Never let a credential in a small JSON body reach the verbose sink. */
    @Bean
    BodyFilter credentialBodyFilter() {
        return BodyFilter.merge(
                BodyFilters.defaultValue(),
                JsonBodyFilters.replaceJsonStringProperty(
                        Set.of(
                                "password",
                                "currentPassword",
                                "newPassword",
                                "oldPassword",
                                "token",
                                "accessToken",
                                "refreshToken",
                                "secret",
                                "otp"),
                        "XXX"));
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

package ch.sthomas.stddivelogger.analytics.dashboard;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class AnalyticsDashboardSecurity {
    @Bean
    @Order(0)
    SecurityFilterChain dashboardSecurity(final HttpSecurity http) throws Exception {
        // Access is restricted by the tailnet-only proxy; CSRF protects job submissions.
        return http.securityMatcher("/ops", "/ops/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                        csp ->
                                                csp.policyDirectives(
                                                        "default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'")))
                .build();
    }
}

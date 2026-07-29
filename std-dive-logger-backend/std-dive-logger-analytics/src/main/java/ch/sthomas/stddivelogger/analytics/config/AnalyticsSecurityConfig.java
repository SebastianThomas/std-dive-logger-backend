package ch.sthomas.stddivelogger.analytics.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!no-security")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class AnalyticsSecurityConfig {

    private static final String SWAGGER = "SWAGGER";

    @Bean
    SecurityFilterChain swaggerFilterChain(final HttpSecurity http) throws Exception {
        // swagger-ui
        http.securityMatcher("/docs/**", "/docs.yaml")
                .authorizeHttpRequests(
                        customizer ->
                                customizer
                                        .requestMatchers("/docs/**", "/docs.yaml")
                                        .hasRole(SWAGGER))
                .httpBasic(withDefaults());
        return http.build();
    }

    @Bean
    SecurityFilterChain analyticsFilterChain(final HttpSecurity http) throws Exception {
        return http.cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        authorize -> authorize.requestMatchers("/actuator/**").permitAll())
                .build();
    }

    @Bean
    public AuthenticationManager swaggerAuthenticationManager(
            @Qualifier("swaggerUserDetailService")
                    final UserDetailsService swaggerUserDetailsService,
            final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(swaggerUserDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    @Qualifier("swaggerUserDetailService")
    InMemoryUserDetailsManager userDetailsService() {
        final var user =
                User.builder()
                        .username("std-dive-logger-swagger")
                        .password("$2a$10$CPPsf4Abg4qRcBQ5uVqnveDtagR83Myl3pg/JLRnGVtHHsxs4aB5i")
                        .roles(SWAGGER)
                        .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearer-key",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}

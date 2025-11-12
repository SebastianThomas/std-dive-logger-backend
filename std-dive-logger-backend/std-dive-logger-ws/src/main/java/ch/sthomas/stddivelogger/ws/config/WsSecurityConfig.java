package ch.sthomas.stddivelogger.ws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Profile("!no-security")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WsSecurityConfig {

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

    /**
     * Set /v1/** to permitAll and remove oauth2ResourceServer if no authentication is used on these
     * endpoints
     */
    @Bean
    SecurityFilterChain bearerFilterChain(final HttpSecurity http) throws Exception {
        http.securityMatcher("/v1/**")
                .authorizeHttpRequests(
                        customizer ->
                                customizer
                                        .requestMatchers(HttpMethod.OPTIONS, "/v1/**")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/v1/info")
                                        .permitAll()
                                        .requestMatchers("/v1/**")
                                        .authenticated())
                .oauth2ResourceServer(
                        resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        http.authorizeHttpRequests(
                customizer ->
                        customizer
                                .dispatcherTypeMatchers(DispatcherType.ERROR)
                                .authenticated()
                                .requestMatchers(HttpMethod.GET, "/check", "/actuator/prometheus")
                                .permitAll()
                                .anyRequest() // deny all others
                                .denyAll());

        return http.build();
    }

    @Bean
    InMemoryUserDetailsManager userDetailsService() {
        final var user =
                User.builder()
                        .username("ubswagger")
                        .password("$2a$10$kkl4QFGZPM2i.TwQPuXhMewLtDBvF.FRohAtMp7dZ4wq8q1N.U7yy")
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

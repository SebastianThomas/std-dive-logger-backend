package ch.sthomas.stddivegastracker.ws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import ch.sthomas.stddivegastracker.ws.auth.DevLoginKey;
import ch.sthomas.stddivegastracker.ws.auth.DiveLoggerJwtDecoder;
import ch.sthomas.stddivegastracker.ws.auth.GasUserAuthenticationConverter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Stateless bearer-token API. {@code /v1/**} needs a dive-logger access JWT (verified by {@link
 * DiveLoggerJwtDecoder}); everything not listed is denied.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final JwtDecoder jwtDecoder,
            final GasUserAuthenticationConverter authenticationConverter,
            final AuthProperties authProperties,
            @Value("${springdoc.api-docs.enabled:true}") final boolean docsEnabled)
            throws Exception {
        return http.cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize -> {
                            authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                            authorize
                                    .requestMatchers(
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/actuator/prometheus",
                                            "/error")
                                    .permitAll();
                            if (authProperties.devLoginEnabled()) {
                                authorize
                                        .requestMatchers(HttpMethod.POST, "/api/dev/login")
                                        .permitAll();
                            }
                            if (docsEnabled) {
                                authorize.requestMatchers("/docs", "/docs/**").permitAll();
                            }
                            authorize.requestMatchers("/v1/**").authenticated();
                            authorize.anyRequest().denyAll();
                        })
                .oauth2ResourceServer(
                        resourceServer ->
                                resourceServer.jwt(
                                        jwt ->
                                                jwt.decoder(jwtDecoder)
                                                        .jwtAuthenticationConverter(
                                                                authenticationConverter)))
                .build();
    }

    /** Fails startup on an unparsable key or when no verification key is available at all. */
    @Bean
    JwtDecoder jwtDecoder(
            final AuthProperties authProperties, final ObjectProvider<DevLoginKey> devLoginKey) {
        final var devKey = devLoginKey.getIfAvailable();
        return new DiveLoggerJwtDecoder(
                authProperties.publicKeys(),
                authProperties.issuer(),
                authProperties.requireEmailVerifiedClaim(),
                devKey == null ? null : devKey.publicKey());
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(final CorsProperties corsProperties) {
        final var config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(Duration.ofHours(1));
        final var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

package ch.sthomas.stddivelogger.importws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import ch.sthomas.stddivelogger.importws.auth.JwtAuthFilter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.SecretKey;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ImportWsSecurityConfig {

    private static final String SWAGGER = "SWAGGER";

    @Bean
    @Profile("!no-security")
    SecurityFilterChain swaggerFilterChain(final HttpSecurity http) {
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
    SecurityFilterChain importWsFilterChain(
            final HttpSecurity http,
            final AuthenticationManager applicationAuthenticationManager,
            final JwtAuthFilter jwtAuthFilter) {
        http.cors(withDefaults())
                .securityMatcher("/v1/import/**")
                .authenticationManager(applicationAuthenticationManager)
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        (request, response, ex1) -> response.sendError(401)))
                .authorizeHttpRequests(
                        (auth) -> {
                            auth.requestMatchers(HttpMethod.OPTIONS)
                                    .permitAll()
                                    .requestMatchers(HttpMethod.GET, "/v1/explore/**")
                                    .permitAll()
                                    .requestMatchers(HttpMethod.POST, "/api/auth/deregister")
                                    .authenticated()
                                    .requestMatchers(HttpMethod.POST, "/api/auth/**")
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated();
                        })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(
            final UserDetailsService customUserDetailsService,
            @Value("${ch.sthomas.stddivelogger.users.check-verified:true}")
                    final boolean checkVerified,
            final SecretKey signingKey) {
        return new JwtAuthFilter(customUserDetailsService, checkVerified, signingKey);
    }

    @Bean
    @Primary
    public AuthenticationManager applicationAuthenticationManager(
            final UserDetailsService userDetailsService, final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    @Profile("!no-security")
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
    @Profile("!no-security")
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
    @Profile("!no-security")
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

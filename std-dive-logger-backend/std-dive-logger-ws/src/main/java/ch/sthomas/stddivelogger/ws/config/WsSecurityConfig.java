package ch.sthomas.stddivelogger.ws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import ch.sthomas.stddivelogger.service.CustomUserDetailsService;
import ch.sthomas.stddivelogger.ws.auth.JwtAuthFilter;
import ch.sthomas.stddivelogger.ws.auth.JwtUtil;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WsSecurityConfig {

    private static final String SWAGGER = "SWAGGER";

    @Order(1)
    @Profile("!no-security")
    @Bean
    SecurityFilterChain swaggerFilterChainSecurity(
            final HttpSecurity http, final AuthenticationManager swaggerAuthenticationManager)
            throws Exception {
        http.securityMatcher("/docs/**", "/docs.yaml")
                .authenticationManager(swaggerAuthenticationManager)
                .authorizeHttpRequests(
                        customizer ->
                                customizer
                                        .requestMatchers("/docs/**", "/docs.yaml")
                                        .hasRole(SWAGGER))
                .httpBasic(withDefaults());

        return http.build();
    }

    @Order(1)
    @Profile("no-security")
    @Bean
    SecurityFilterChain swaggerFilterChainNoSecurity(final HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.securityMatcher("/docs/**", "/docs.yaml")
                .authorizeHttpRequests(
                        authorize ->
                                authorize.requestMatchers("/docs/**", "/docs.yaml").permitAll());
        return http.build();
    }

    @Order(2)
    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final AuthenticationManager applicationAuthenticationManager,
            final JwtAuthFilter jwtAuthFilter)
            throws Exception {
        http.securityMatcher("/v1/**", "/api/**")
                .authenticationManager(applicationAuthenticationManager)
                .csrf(withDefaults())
                .httpBasic(withDefaults())
                .formLogin(withDefaults())
                .authorizeHttpRequests(
                        (auth) ->
                                auth.requestMatchers(HttpMethod.GET, "/v1/explore")
                                        .permitAll()
                                        .requestMatchers("/api/auth/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(
            final JwtUtil jwtUtil, final CustomUserDetailsService customUserDetailsService) {
        return new JwtAuthFilter(jwtUtil, customUserDetailsService);
    }

    @Bean
    public AuthenticationManager applicationAuthenticationManager(
            final CustomUserDetailsService userDetailsService,
            final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Profile("!no-security")
    @Bean
    public AuthenticationManager swaggerAuthenticationManager(
            final UserDetailsService swaggerUserDetailsService,
            final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(swaggerUserDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    // @Bean
    // SecurityFilterChain bearerFilterChain(final HttpSecurity http) throws Exception {
    //     http.securityMatcher("/v1/**")
    //             .authorizeHttpRequests(
    //                     customizer ->
    //                             customizer
    //                                     .requestMatchers(HttpMethod.OPTIONS, "/v1/**")
    //                                     .permitAll()
    //                                     .requestMatchers(HttpMethod.GET, "/v1/info")
    //                                     .permitAll()
    //                                     .requestMatchers("/v1/**")
    //                                     .authenticated())
    //             .oauth2ResourceServer(resourceServer -> resourceServer.jwt(withDefaults()));

    //     http.authorizeHttpRequests(
    //             customizer ->
    //                     customizer
    //                             .dispatcherTypeMatchers(DispatcherType.ERROR)
    //                             .authenticated()
    //                             .requestMatchers(HttpMethod.GET, "/check",
    // "/actuator/prometheus")
    //                             .permitAll()
    //                             .anyRequest() // deny all others
    //                             .denyAll());

    //     return http.build();
    // }

    @Profile("!no-security")
    @Bean
    InMemoryUserDetailsManager swaggerUserDetailsService() {
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

    @Profile("!no-security")
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

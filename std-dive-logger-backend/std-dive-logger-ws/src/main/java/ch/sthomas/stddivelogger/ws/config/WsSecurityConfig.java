package ch.sthomas.stddivelogger.ws.config;

import static org.springframework.security.config.Customizer.withDefaults;

import ch.sthomas.stddivelogger.ws.auth.JwtAuthFilter;
import ch.sthomas.stddivelogger.ws.auth.JwtUtil;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WsSecurityConfig {

    private static final String SWAGGER = "SWAGGER";
    private static final Logger logger = LoggerFactory.getLogger(WsSecurityConfig.class);

    @Profile("!no-security")
    @Bean
    SecurityFilterChain swaggerFilterChainSecurity(
            final HttpSecurity http,
            @Qualifier("swaggerAuthManager")
                    final AuthenticationManager swaggerAuthenticationManager)
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

    @Bean
    SecurityFilterChain securityFilterChain(
            final HttpSecurity http,
            final AuthenticationManager applicationAuthenticationManager,
            final JwtAuthFilter jwtAuthFilter)
            throws Exception {
        http.securityMatcher("/v1/**", "/api/**")
                .authenticationManager(applicationAuthenticationManager)
                .csrf(AbstractHttpConfigurer::disable)
                // .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        (request, response, ex1) -> response.sendError(401)))
                .authorizeHttpRequests(
                        (auth) -> {
                            logger.info("Authorize Customizer");
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
            final JwtUtil jwtUtil, final UserDetailsService customUserDetailsService) {
        return new JwtAuthFilter(jwtUtil, customUserDetailsService);
    }

    @Bean
    @Primary
    public AuthenticationManager applicationAuthenticationManager(
            final UserDetailsService userDetailsService, final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Profile("!no-security")
    @Bean
    @Qualifier("swaggerAuthManager")
    public AuthenticationManager swaggerAuthenticationManager(
            @Qualifier("swaggerUserDetailsService")
                    final UserDetailsService swaggerUserDetailsService,
            final PasswordEncoder passwordEncoder) {
        final var authenticationProvider = new DaoAuthenticationProvider(swaggerUserDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Profile("!no-security")
    @Bean
    @Qualifier("swaggerUserDetailsService")
    InMemoryUserDetailsManager swaggerUserDetailsService() {
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

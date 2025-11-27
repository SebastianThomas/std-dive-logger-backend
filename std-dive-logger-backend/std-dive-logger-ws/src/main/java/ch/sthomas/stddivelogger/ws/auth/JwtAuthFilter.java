package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.service.CustomUserDetailsService;

import io.jsonwebtoken.ExpiredJwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthFilter(final JwtUtil jwtUtil, final CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            final @NotNull HttpServletRequest request,
            final @NotNull HttpServletResponse response,
            final @NotNull FilterChain filterChain)
            throws ServletException, IOException {

        final var authHeader = request.getHeader("Authorization");

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                final var token = authHeader.substring(7);
                final var claimedUsername =
                        jwtUtil.extractUsername(token, JwtUtil.TokenType.ACCESS_TOKEN);

                if (claimedUsername != null
                        && SecurityContextHolder.getContext().getAuthentication() == null) {
                    final var userDetails = userDetailsService.loadUserByUsername(claimedUsername);
                    final var username = userDetails.getUsername();

                    if (jwtUtil.isTokenValid(token, username, JwtUtil.TokenType.ACCESS_TOKEN)) {
                        final var authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (final ExpiredJwtException e) {
            // TODO: Can we throw here?
            throw new UnauthorizedException("Token has expired");
        }

        filterChain.doFilter(request, response);
    }
}

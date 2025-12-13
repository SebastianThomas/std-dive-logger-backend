package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.model.user.User;

import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final boolean checkVerified;

    public JwtAuthFilter(
            final JwtUtil jwtUtil,
            final UserDetailsService userDetailsService,
            final boolean checkVerified) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.checkVerified = checkVerified;
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

                    if (checkVerified
                            && userDetails instanceof final User user
                            && !user.emailVerified()) {
                        response.sendError(
                                HttpServletResponse.SC_UNAUTHORIZED, "USER_EMAIL_UNVERIFIED");
                        return;
                    }

                    if (jwtUtil.isTokenValid(token, username, JwtUtil.TokenType.ACCESS_TOKEN)) {
                        final var authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (final JwtException e) {
            logger.info("Exception parsing JWT", e);
        }

        filterChain.doFilter(request, response);
    }
}

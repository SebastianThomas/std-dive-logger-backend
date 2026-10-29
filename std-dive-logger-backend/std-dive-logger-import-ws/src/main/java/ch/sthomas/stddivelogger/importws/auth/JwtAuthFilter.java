package ch.sthomas.stddivelogger.importws.auth;

import ch.sthomas.stddivelogger.model.user.User;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;

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
    private final UserDetailsService userDetailsService;
    private final boolean checkVerified;
    private final JwtParser accessTokenParser;

    public JwtAuthFilter(
            final UserDetailsService userDetailsService,
            final boolean checkVerified,
            final JwtParser accessTokenParser) {
        this.userDetailsService = userDetailsService;
        this.checkVerified = checkVerified;
        this.accessTokenParser = accessTokenParser;
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
                final var claimedUsername = JwtUtil.extractUsername(token, accessTokenParser);

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

                    if (JwtUtil.isTokenValid(token, username, accessTokenParser)) {
                        final var authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (final JwtException | IllegalArgumentException e) {
            // Unusable token (other key, HS256, no iss, malformed): unauthenticated -> 401.
            if (!(e instanceof ExpiredJwtException)) {
                logger.info("Exception parsing JWT", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}

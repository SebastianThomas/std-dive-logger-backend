package ch.sthomas.stddivelogger.importws.auth;

import ch.sthomas.stddivelogger.model.user.User;

import io.jsonwebtoken.ExpiredJwtException;
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

import javax.crypto.SecretKey;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final UserDetailsService userDetailsService;
    private final boolean checkVerified;
    private final SecretKey signingKey;

    public JwtAuthFilter(
            final UserDetailsService userDetailsService,
            final boolean checkVerified,
            final SecretKey signingKey) {
        this.userDetailsService = userDetailsService;
        this.checkVerified = checkVerified;
        this.signingKey = signingKey;
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
                final var claimedUsername = JwtUtil.extractUsername(token, signingKey);

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

                    if (JwtUtil.isTokenValid(token, username, signingKey)) {
                        final var authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        } catch (final JwtException e) {
            if (!(e instanceof ExpiredJwtException)) {
                logger.info("Exception parsing JWT", e);
            }
        }

        filterChain.doFilter(request, response);
    }
}

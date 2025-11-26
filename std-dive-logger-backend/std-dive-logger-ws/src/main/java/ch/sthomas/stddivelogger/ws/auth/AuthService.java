package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.model.controller.auth.AuthRequest;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;

import jakarta.annotation.Nullable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final JwtUtil jwtUtil;
    private final AuthenticationManager applicationAuthenticationManager;
    public final boolean sameSiteCookie;

    public AuthService(
            final JwtUtil jwtUtil,
            final AuthenticationManager applicationAuthenticationManager,
            @Value("${ch.sthomas.stddivelogger.ws.security.same_site_cookie:true}")
                    final boolean sameSiteCookie) {
        this.jwtUtil = jwtUtil;
        this.applicationAuthenticationManager = applicationAuthenticationManager;
        this.sameSiteCookie = sameSiteCookie;
    }

    public String refresh(@Nullable final String refreshToken) {
        final var username = assertValidForUser(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        if (!jwtUtil.isTokenValid(refreshToken, username, JwtUtil.TokenType.REFRESH_TOKEN)) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        return jwtUtil.generateToken(username, JwtUtil.TokenType.ACCESS_TOKEN);
    }

    public AuthResponse.AuthResponseWithRefreshToken login(final AuthRequest request) {
        final var auth =
                applicationAuthenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(), request.password()));

        final var token = jwtUtil.generateToken(auth.getName(), JwtUtil.TokenType.ACCESS_TOKEN);
        final var refreshToken =
                jwtUtil.generateToken(auth.getName(), JwtUtil.TokenType.REFRESH_TOKEN);

        final var responseCookie = createRefreshTokenCookie(refreshToken, Duration.ofDays(30));
        return new AuthResponse.AuthResponseWithRefreshToken(token, responseCookie);
    }

    public void logout(final String refreshToken) {
        assertValidForUser(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        jwtUtil.deleteRefreshToken(refreshToken);
    }

    private String assertValidForUser(
            @Nullable final String refreshToken, final JwtUtil.TokenType tokenType) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        final var username = jwtUtil.extractUsername(refreshToken, tokenType);
        if (username == null) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        return username;
    }

    public ResponseCookie createRefreshTokenCookie(
            final String refreshToken, final Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite(sameSiteCookie ? "Strict" : "None")
                .path("/api/auth/")
                .maxAge(maxAge.toSeconds())
                .build();
    }
}

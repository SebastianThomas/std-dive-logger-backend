package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.model.controller.auth.AuthRequest;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;

import jakarta.annotation.Nullable;

import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

    private final JwtUtil jwtUtil;
    private final AuthenticationManager applicationAuthenticationManager;

    public AuthService(
            final JwtUtil jwtUtil, final AuthenticationManager applicationAuthenticationManager) {
        this.jwtUtil = jwtUtil;
        this.applicationAuthenticationManager = applicationAuthenticationManager;
    }

    public String refresh(@Nullable final String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        final var username = jwtUtil.extractUsername(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
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

        final var responseCookie =
                ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path("/api/auth/refresh")
                        .maxAge(60 * 60 * 24 * 30)
                        .build();
        return new AuthResponse.AuthResponseWithRefreshToken(token, responseCookie);
    }

    public void logout(final String refreshToken) {
        final var username = jwtUtil.extractUsername(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        if (!jwtUtil.isTokenValid(refreshToken, username, JwtUtil.TokenType.REFRESH_TOKEN)) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        jwtUtil.deleteRefreshToken(refreshToken);
    }
}

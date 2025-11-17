package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.model.controller.auth.AuthRequest;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JwtUtil jwtUtil;
    private final AuthenticationManager applicationAuthenticationManager;

    public AuthService(
            final JwtUtil jwtUtil, final AuthenticationManager applicationAuthenticationManager) {
        this.jwtUtil = jwtUtil;
        this.applicationAuthenticationManager = applicationAuthenticationManager;
    }

    public String refresh(final String refreshToken) {
        final var username = jwtUtil.extractUsername(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        if (!jwtUtil.isTokenValid(refreshToken, username, JwtUtil.TokenType.REFRESH_TOKEN)) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        return jwtUtil.generateToken(username, JwtUtil.TokenType.ACCESS_TOKEN);
    }

    public AuthResponse login(final AuthRequest request) {
        final var auth =
                applicationAuthenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(), request.password()));

        final var token = jwtUtil.generateToken(auth.getName(), JwtUtil.TokenType.ACCESS_TOKEN);
        final var refreshToken =
                jwtUtil.generateToken(auth.getName(), JwtUtil.TokenType.REFRESH_TOKEN);
        return new AuthResponse(token, refreshToken);
    }

    public void logout(final String refreshToken) {
        final var username = jwtUtil.extractUsername(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        if (!jwtUtil.isTokenValid(refreshToken, username, JwtUtil.TokenType.REFRESH_TOKEN)) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        jwtUtil.deleteRefreshToken(refreshToken);
    }
}

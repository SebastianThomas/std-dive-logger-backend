package ch.sthomas.stddivelogger.ws.auth;

import ch.sthomas.stddivelogger.data.repository.RefreshTokenRepository;
import ch.sthomas.stddivelogger.data.service.UserDataService;
import ch.sthomas.stddivelogger.model.controller.auth.AuthRequest;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.notification.AccountRequestType;
import ch.sthomas.stddivelogger.service.PushService;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.utils.SecurityUtils;

import io.jsonwebtoken.JwtException;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class AuthService {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final JwtUtil jwtUtil;
    private final AuthenticationManager applicationAuthenticationManager;
    public final boolean sameSiteCookie;
    private final UserDataService userDataService;
    private final UserService userService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PushService pushService;

    public AuthService(
            final JwtUtil jwtUtil,
            final AuthenticationManager applicationAuthenticationManager,
            @Value("${ch.sthomas.stddivelogger.ws.security.same_site_cookie:true}")
                    final boolean sameSiteCookie,
            final UserDataService userDataService,
            final UserService userService,
            final RefreshTokenRepository refreshTokenRepository,
            final PushService pushService) {
        this.jwtUtil = jwtUtil;
        this.applicationAuthenticationManager = applicationAuthenticationManager;
        this.sameSiteCookie = sameSiteCookie;
        this.userDataService = userDataService;
        this.userService = userService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.pushService = pushService;
    }

    /** A 401 for any unusable cookie (expired, revoked, tampered) - never a 500. */
    public String refresh(@Nullable final String refreshToken) {
        final var username = assertValidForUser(refreshToken);
        try {
            // assertValidForUser already threw above if refreshToken was null.
            if (!jwtUtil.isTokenValid(
                    Objects.requireNonNull(refreshToken),
                    username,
                    JwtUtil.TokenType.REFRESH_TOKEN)) {
                throw new UnauthorizedException("Invalid refresh token.");
            }
        } catch (final JwtException e) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        return jwtUtil.generateAccessToken(username);
    }

    public ResponseEntity<AuthResponse> login(final AuthRequest request) {
        final var auth =
                applicationAuthenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email().trim().toLowerCase(), request.password()));
        return createLoginResponse(auth.getName());
    }

    public ResponseEntity<AuthResponse> tokenLogin(final String token) {
        final var authRequest =
                userDataService
                        .findAndDeleteAccountRequestEntityById(SecurityUtils.hashToken(token))
                        .filter(a -> a.type() == AccountRequestType.LOGIN)
                        .orElseThrow(() -> new NoSuchElementException("No request for this ID."));
        return createLoginResponse(authRequest.user().getUsername());
    }

    /**
     * Stores the new password (same policy as signup), revokes every session and push subscription
     * of the account, then signs this device back in with a fresh session.
     */
    @Transactional
    public ResponseEntity<AuthResponse> changePassword(
            final long userId, final String currentPassword, final String newPassword) {
        final var user = userService.changePassword(userId, currentPassword, newPassword);
        revokeAllSessions(userId);
        return createLoginResponse(user.email());
    }

    /** "Log out on all devices": every refresh token and push subscription of the account. */
    @Transactional
    public void revokeAllSessions(final long userId) {
        final int tokens = refreshTokenRepository.deleteAllByUserId(userId);
        final int subscriptions = pushService.deleteAllForUser(userId);
        logger.info(
                "Revoked {} session(s) and {} push subscription(s) of user {}",
                tokens,
                subscriptions,
                userId);
    }

    private ResponseEntity<AuthResponse> createLoginResponse(final String username) {
        final var user =
                userDataService
                        .findUserByEmail(username)
                        .orElseThrow(() -> new UnauthorizedException("Unknown user."));
        final var token = jwtUtil.generateAccessToken(username);
        final var refreshToken = jwtUtil.generateRefreshToken(user.id(), username);

        final var responseCookie =
                createRefreshTokenCookie(refreshToken, JwtUtil.REFRESH_TOKEN_LIFETIME);
        final var login = new AuthResponse.AuthResponseWithRefreshToken(token, responseCookie);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, login.refreshToken().toString())
                .body(login.toAuthResponse());
    }

    /** Lenient: an expired or unknown token has nothing left to delete, the cookie still goes. */
    public void logout(final String refreshToken) {
        try {
            assertValidForUser(refreshToken);
            jwtUtil.deleteRefreshToken(refreshToken);
        } catch (final UnauthorizedException | JwtException e) {
            logger.debug("Logout with an unusable refresh token - nothing to revoke.");
        }
    }

    private String assertValidForUser(@Nullable final String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
        final String username;
        try {
            username = jwtUtil.extractUsername(refreshToken, JwtUtil.TokenType.REFRESH_TOKEN);
        } catch (final JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid refresh token.");
        }
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
                .path("/api/auth")
                .maxAge(maxAge.toSeconds())
                .build();
    }
}

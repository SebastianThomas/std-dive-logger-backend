package ch.sthomas.stddivelogger.ws.controller;

import static ch.sthomas.stddivelogger.utils.LogSanitizer.sanitizeEmail;
import static ch.sthomas.stddivelogger.utils.LogSanitizer.sanitizePassword;

import ch.sthomas.stddivelogger.model.controller.auth.AuthRequest;
import ch.sthomas.stddivelogger.model.controller.auth.AuthResponse;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.ws.auth.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;
    private final AuthService authService;

    public AuthController(final UserService userService, final AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @Operation(summary = "Log in")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody final AuthRequest request) {
        final var login = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, login.refreshToken().toString())
                .body(login.toAuthResponse());
    }

    @Operation(summary = "Get a new access token")
    @PostMapping("/refresh")
    public String refresh(
            @CookieValue(value = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = true)
                    final String refreshToken) {
        return authService.refresh(refreshToken);
    }

    public record SignupRequest(
            @NotNull @NotBlank @Email String email,
            @NotNull @NotBlank String password,
            @NotNull @NotBlank String name) {
        @NotNull
        @Override
        public String toString() {
            return String.format(
                    "SignupRequest {name: %s, email: %s, password: %s}",
                    name, sanitizeEmail(email), sanitizePassword(password));
        }
    }

    @Operation(
            summary = "Create a new user",
            responses = {
                @ApiResponse(responseCode = "200", description = "Returns the newly created user."),
                @ApiResponse(
                        responseCode = "400",
                        description = "The email or password are not valid.")
            })
    @PostMapping("/signup")
    public FrontendUser signup(@Valid @RequestBody final SignupRequest request) {
        return userService
                .createUser(request.email, request.password, request.name)
                .toFrontendModel();
    }

    @PostMapping("/verify-email")
    public FrontendUser verifyEmail(@RequestParam(name = "token") @NotBlank final String token) {
        return userService.setVerified(token).toFrontendModel();
    }

    @Operation(summary = "Invalidate the given refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = AuthService.REFRESH_TOKEN_COOKIE_NAME, required = false)
                    final String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        final var deleteCookie = authService.createRefreshTokenCookie("", Duration.ofSeconds(0));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, deleteCookie.toString()).build();
    }

    @PostMapping("/deregister")
    public void deregister(@AuthenticationPrincipal final User user) {
        userService.deleteUser(user);
    }
}

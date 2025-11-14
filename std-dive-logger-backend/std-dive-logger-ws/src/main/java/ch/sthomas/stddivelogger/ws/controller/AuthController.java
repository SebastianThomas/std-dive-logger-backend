package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.InvalidPasswordException;
import ch.sthomas.stddivelogger.model.user.FrontendUser;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.UserService;
import ch.sthomas.stddivelogger.ws.auth.JwtUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final AuthenticationManager applicationAuthenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(
            final AuthenticationManager applicationAuthenticationManager,
            final JwtUtil jwtUtil,
            final UserService userService) {
        this.applicationAuthenticationManager = applicationAuthenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    public record AuthRequest(
            @NotNull @NotBlank @Email String email, @NotNull @NotBlank String password) {}

    public record AuthResponse(String token) {}

    @Operation(summary = "Log in")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody final AuthRequest request) {
        final var auth =
                applicationAuthenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.email(), request.password()));

        final var token = jwtUtil.generateToken(auth.getName());
        return new AuthResponse(token);
    }

    public record SignupRequest(
            @NotNull @NotBlank @Email String email, @NotNull @NotBlank String password) {}

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
        return userService.createUser(request.email, request.password).toFrontendModel();
    }

    @PostMapping("/logout")
    public void logout(@AuthenticationPrincipal final User user) {
        // TODO
        logger.info("Logout of user {}", user.email().substring(user.email().indexOf('@')));
    }

    @PostMapping("/deregister")
    public void deregister(@AuthenticationPrincipal final User user) {
        userService.deleteUser(user);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Problem> handleInvalidPasswordException(
            final InvalidPasswordException exception) {
        return ResponseEntity.badRequest()
                .body(
                        Problem.builder()
                                .withStatus(Status.BAD_REQUEST)
                                .withTitle(exception.getMessage())
                                .withDetail(String.join(",\n", exception.details()))
                                .build());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentialsException(final BadCredentialsException ex) {
        logger.warn("Invalid Credentials", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Problem.valueOf(Status.BAD_REQUEST, "Invalid Credentials"));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<?> handleDisabledException(final DisabledException ex) {
        logger.warn("User disabled", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Problem.valueOf(Status.BAD_REQUEST, "User disabled"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthenticationException(final AuthenticationException ex) {
        logger.warn("Authentication failed", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Problem.valueOf(Status.BAD_REQUEST, "Authentication failed"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Problem> handleIllegalArgumentException(
            final IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(
                        Problem.builder()
                                .withStatus(Status.BAD_REQUEST)
                                .withTitle(e.getMessage())
                                .build());
    }
}

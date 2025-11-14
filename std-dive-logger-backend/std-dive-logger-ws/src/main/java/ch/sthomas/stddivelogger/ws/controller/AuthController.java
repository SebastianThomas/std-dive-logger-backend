package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.ws.auth.JwtUtil;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager applicationAuthenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(final AuthenticationManager applicationAuthenticationManager, final JwtUtil jwtUtil) {
        this.applicationAuthenticationManager = applicationAuthenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public record AuthRequest(String username, String password) {}

    public record AuthResponse(String token) {}

    @PostMapping("/login")
    public AuthResponse login(@RequestBody final AuthRequest request) {
        final var auth =
                applicationAuthenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.username(), request.password()));

        final var token = jwtUtil.generateToken(auth.getName());
        return new AuthResponse(token);
    }
}

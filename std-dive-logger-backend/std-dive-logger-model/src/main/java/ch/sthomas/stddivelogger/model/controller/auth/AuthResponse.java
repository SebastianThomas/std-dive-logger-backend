package ch.sthomas.stddivelogger.model.controller.auth;

import org.springframework.http.ResponseCookie;

public record AuthResponse(String accessToken) {
    public record AuthResponseWithRefreshToken(String accessToken, ResponseCookie refreshToken) {
        public AuthResponse toAuthResponse() {
            return new AuthResponse(accessToken);
        }
    }
}

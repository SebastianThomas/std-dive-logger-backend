package ch.sthomas.stddivelogger.model.controller.auth;

import static ch.sthomas.stddivelogger.utils.LogSanitizer.sanitizeEmail;
import static ch.sthomas.stddivelogger.utils.LogSanitizer.sanitizePassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AuthRequest(
        @NotNull @NotBlank @Email String email, @NotNull @NotBlank String password) {

    @NotNull
    @Override
    public String toString() {
        return String.format(
                "%s {email: %s, password: %s}",
                getClass().getSimpleName(), sanitizeEmail(email), sanitizePassword(password));
    }
}

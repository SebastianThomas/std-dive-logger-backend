package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.ReminderService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.constraints.Positive;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home-page reminders (dive anniversaries + the "dive again" nudge). Listing is part of {@code GET
 * /v1/home}; this endpoint is just the dismiss.
 */
@RestController
@RequestMapping("/v1/reminders")
@Validated
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(final ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Operation(summary = "Dismiss a home-page reminder")
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal final @Nullable User user,
            @PathVariable("id") @Positive final long id) {
        if (user == null) {
            throw new UnauthorizedException("Log in to dismiss a reminder.");
        }
        return reminderService.dismiss(user, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}

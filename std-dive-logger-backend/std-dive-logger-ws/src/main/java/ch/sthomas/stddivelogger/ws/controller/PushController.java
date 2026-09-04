package ch.sthomas.stddivelogger.ws.controller;

import ch.sthomas.stddivelogger.model.exception.UnauthorizedException;
import ch.sthomas.stddivelogger.model.push.PushSubscriptionRequest;
import ch.sthomas.stddivelogger.model.user.User;
import ch.sthomas.stddivelogger.service.PushService;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web Push opt-in: the browser fetches the VAPID public key, calls {@code pushManager.subscribe()},
 * and POSTs the resulting {@link PushSubscriptionRequest} here. Unsubscribing (or the browser
 * rotating the endpoint) DELETEs it.
 */
@RestController
@RequestMapping("/v1/push")
public class PushController {

    private final PushService pushService;

    public PushController(final PushService pushService) {
        this.pushService = pushService;
    }

    @Operation(
            summary = "VAPID public key for pushManager.subscribe() (empty string if push is off)")
    @GetMapping("/public-key")
    public Map<String, Object> publicKey() {
        return Map.of("publicKey", pushService.publicKey(), "enabled", pushService.isEnabled());
    }

    @Operation(summary = "Register this browser for push")
    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal final @Nullable User user,
            @Valid @RequestBody final PushSubscriptionRequest body,
            @RequestHeader(value = "User-Agent", required = false)
                    final @Nullable String userAgent) {
        if (user == null) {
            throw new UnauthorizedException("Log in to enable push notifications.");
        }
        pushService.subscribe(user.id(), body, userAgent);
        return ResponseEntity.noContent().build();
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {}

    @Operation(summary = "Unregister this browser from push")
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal final @Nullable User user,
            @Valid @RequestBody final UnsubscribeRequest body) {
        if (user == null) {
            throw new UnauthorizedException("Log in to change push notifications.");
        }
        pushService.unsubscribe(user.id(), body.endpoint());
        return ResponseEntity.noContent().build();
    }
}

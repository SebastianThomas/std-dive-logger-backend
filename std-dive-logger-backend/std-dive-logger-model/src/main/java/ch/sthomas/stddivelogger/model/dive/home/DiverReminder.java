package ch.sthomas.stddivelogger.model.dive.home;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One time-sensitive prompt for the home page (and, when the diver has opted in, a web push): a
 * dive anniversary ("3 years ago today ...") or a dynamic "time to go diving again" nudge.
 *
 * <p>Computed and stored per diver by the analytics deployable (recomputed daily, since "today"
 * moves), surfaced by {@code GET /v1/home} while it's still {@code relevantOn}..{@code expiresAt}
 * and not dismissed. Dismissing is a {@code POST /v1/reminders/{id}/dismiss}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiverReminder(
        long id,
        ReminderKind kind,
        String title,
        String body,
        // Deep-link target for the banner (the representative dive for an anniversary); null for a
        // nudge.
        @Nullable Long diveId,
        // Anniversary only: how many years ago. null for a nudge.
        @Nullable Integer yearsAgo,
        LocalDate relevantOn,
        Instant createdAt) {}

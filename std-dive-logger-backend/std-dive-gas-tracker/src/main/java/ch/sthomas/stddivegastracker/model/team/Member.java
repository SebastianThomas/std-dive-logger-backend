package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * API.md §2 {@code Member}. Teammates see each other's username, never an email address (DOMAIN
 * §2). {@code username} is null while PENDING, and until a token carrying it was seen.
 */
public record Member(
        long memberId,
        @Nullable String username,
        String displayName,
        Role role,
        MemberStatus status,
        Instant joinedAt,
        @Nullable Instant leftAt) {}

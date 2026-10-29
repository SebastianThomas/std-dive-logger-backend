package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * API.md §2 {@code Member}. While PENDING, {@code email} is the invited address and {@code
 * displayName} its local part (no user yet); {@code email} is null (and omitted from the JSON) for
 * a user whose address was taken over by another account and who has not signed in since (DOMAIN
 * §2).
 */
public record Member(
        long memberId,
        @Nullable String email,
        String displayName,
        Role role,
        MemberStatus status,
        Instant joinedAt,
        @Nullable Instant leftAt) {}

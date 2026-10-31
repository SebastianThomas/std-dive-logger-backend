package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

/**
 * API.md §2 {@code POST …/members}: by username (someone already using the app), or by email. The
 * service normalises the email.
 */
public record AddMemberRequest(@Nullable String username, @Nullable String email, Role role) {

    public AddMemberRequest {
        username = username == null ? null : username.strip().replaceFirst("^@", "");
        if ((username == null || username.isEmpty()) && email == null) {
            throw new IllegalArgumentException("Enter a username.");
        }
        role = role == null ? Role.MEMBER : role;
    }
}

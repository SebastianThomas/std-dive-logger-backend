package ch.sthomas.stddivegastracker.model.team;

/** API.md §2 {@code POST …/members}: someone already using the app, by username. */
public record AddMemberRequest(String username, Role role) {

    public AddMemberRequest {
        username = username == null ? "" : username.strip().replaceFirst("^@", "");
        if (username.isEmpty()) {
            throw new IllegalArgumentException("Enter a username.");
        }
        role = role == null ? Role.MEMBER : role;
    }
}

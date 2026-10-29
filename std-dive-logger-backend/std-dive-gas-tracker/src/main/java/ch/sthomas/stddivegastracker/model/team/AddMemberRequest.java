package ch.sthomas.stddivegastracker.model.team;

/** API.md §2 {@code POST …/members}. The email is normalised by the service. */
public record AddMemberRequest(String email, Role role) {

    public AddMemberRequest {
        if (email == null) {
            throw new IllegalArgumentException("Email is required.");
        }
        role = role == null ? Role.MEMBER : role;
    }
}

package ch.sthomas.stddivegastracker.model.team;

/** API.md shared type: who paid, recorded or created something. PENDING: email local part. */
public record MemberRef(long memberId, String displayName) {}

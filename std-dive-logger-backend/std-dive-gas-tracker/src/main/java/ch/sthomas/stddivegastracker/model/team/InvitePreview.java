package ch.sthomas.stddivegastracker.model.team;

/** API.md §2: what an invite link leads to, shown before joining. */
public record InvitePreview(long teamId, String teamName, boolean alreadyMember) {}

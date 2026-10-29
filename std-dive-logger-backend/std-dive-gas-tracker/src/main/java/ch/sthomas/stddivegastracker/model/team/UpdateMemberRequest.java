package ch.sthomas.stddivegastracker.model.team;

import org.jspecify.annotations.Nullable;

/** API.md §2 {@code PUT …/members/{memberId}}; an absent field is left as it is. */
public record UpdateMemberRequest(@Nullable Role role, @Nullable MemberStatus status) {}

package ch.sthomas.stddivegastracker.model.entity;

import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;

import jakarta.persistence.*;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * A person's membership in one team (DOMAIN §2). Exactly one of {@code userId} and {@code
 * invitedEmail} is set: an invited email without a user is PENDING until that email's first
 * verified sign-in binds it (see {@code UserDataService}); {@code leftAt} set means LEFT.
 */
@Entity
@Table(name = "t_team_member")
@SuppressWarnings("NullAway.Init")
public class TeamMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pk_team_member_id", nullable = false)
    private Long id;

    @Column(name = "fk_team_id", nullable = false)
    private Long teamId;

    @Column(name = "fk_user_id")
    private @Nullable Long userId;

    @Column(name = "invited_email", length = 320)
    private @Nullable String invitedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private @Nullable Instant leftAt;

    public TeamMemberEntity() {}

    /** A member who is a known user. */
    public TeamMemberEntity(
            final Long teamId, final Long userId, final Role role, final Instant joinedAt) {
        this.teamId = teamId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    /** A PENDING member; {@code invitedEmail} is normalised (trimmed, lower-cased). */
    public TeamMemberEntity(
            final Long teamId, final String invitedEmail, final Role role, final Instant joinedAt) {
        this.teamId = teamId;
        this.invitedEmail = invitedEmail;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public @Nullable Long getUserId() {
        return userId;
    }

    public @Nullable String getInvitedEmail() {
        return invitedEmail;
    }

    /** Binds a PENDING member to the user who signed in with its invited email. */
    public void bindTo(final Long userId) {
        this.userId = userId;
        this.invitedEmail = null;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(final Role role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    /** Reset when a LEFT member is re-added: the unique (team, user) row is reused. */
    public void setJoinedAt(final Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public @Nullable Instant getLeftAt() {
        return leftAt;
    }

    /** Sets (member LEFT) or clears (member back) the leave time. */
    public void setLeftAt(final @Nullable Instant leftAt) {
        this.leftAt = leftAt;
    }

    /** ACTIVE: bound to a user and not left - the only state that may use the API (DOMAIN §10). */
    public boolean isActive() {
        return userId != null && leftAt == null;
    }

    public MemberStatus status() {
        if (leftAt != null) {
            return MemberStatus.LEFT;
        }
        return userId == null ? MemberStatus.PENDING : MemberStatus.ACTIVE;
    }

    public TeamMembership toMembership() {
        return new TeamMembership(id, teamId, role);
    }

    /**
     * {@code email} and {@code displayName} are the user's, or the invited address's while PENDING;
     * {@code email} is null for a user whose address moved to another account.
     */
    public Member toRecord(final @Nullable String username, final String displayName) {
        return new Member(id, username, displayName, role, status(), joinedAt, leftAt);
    }
}

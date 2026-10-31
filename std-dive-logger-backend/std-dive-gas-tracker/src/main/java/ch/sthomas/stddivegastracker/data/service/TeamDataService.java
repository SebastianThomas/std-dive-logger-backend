package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.data.repository.UserRepository;
import ch.sthomas.stddivegastracker.model.entity.TeamEntity;
import ch.sthomas.stddivegastracker.model.entity.TeamMemberEntity;
import ch.sthomas.stddivegastracker.model.entity.UserEntity;
import ch.sthomas.stddivegastracker.model.team.InviteLink;
import ch.sthomas.stddivegastracker.model.team.InvitePreview;
import ch.sthomas.stddivegastracker.model.team.Member;
import ch.sthomas.stddivegastracker.model.team.MemberStatus;
import ch.sthomas.stddivegastracker.model.team.Role;
import ch.sthomas.stddivegastracker.model.team.Team;
import ch.sthomas.stddivegastracker.model.team.TeamInput;
import ch.sthomas.stddivegastracker.model.team.TeamMembership;
import ch.sthomas.stddivegastracker.model.team.TeamSummary;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Team + TeamMember tables (DOMAIN §2). Every membership change locks the team row first, so the
 * last-admin guard (DOMAIN §10) cannot be raced by two admins demoting each other.
 */
@Service
public class TeamDataService {

    private static final String INVALID_INVITE = "This invite link is not valid any more.";

    private static final Comparator<Member> MEMBER_ORDER =
            Comparator.comparing(Member::status)
                    .thenComparing(Member::displayName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Member::memberId);

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final UserRepository userRepository;

    public TeamDataService(
            final TeamRepository teamRepository,
            final TeamMemberRepository memberRepository,
            final UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** Empty when the team is unknown, the user never joined it, or has left it. */
    @Transactional(readOnly = true)
    public Optional<TeamMembership> findActiveMembership(final long teamId, final long userId) {
        return memberRepository
                .findByTeamIdAndUserId(teamId, userId)
                .filter(TeamMemberEntity::isActive)
                .map(TeamMemberEntity::toMembership);
    }

    @Transactional(readOnly = true)
    public List<TeamSummary> activeTeams(final long userId) {
        return memberRepository.findActiveTeamSummaries(userId);
    }

    /** The creator becomes the first ADMIN (DOMAIN §10). */
    @Transactional
    public Team createTeam(final long userId, final TeamInput input) {
        final var team = new TeamEntity();
        apply(team, input);
        teamRepository.save(team);
        final var admin =
                memberRepository.save(
                        new TeamMemberEntity(team.getId(), userId, Role.ADMIN, Instant.now()));
        return team.toRecord(admin.toMembership());
    }

    @Transactional(readOnly = true)
    public Team getTeam(final TeamMembership caller) {
        return findTeam(caller.teamId()).toRecord(caller);
    }

    @Transactional
    public Team updateTeam(final TeamMembership caller, final TeamInput input) {
        final var team = findTeam(caller.teamId());
        apply(team, input);
        return team.toRecord(caller);
    }

    @Transactional(readOnly = true)
    public List<Member> members(final long teamId) {
        return toMembers(memberRepository.findByTeamId(teamId));
    }

    /**
     * {@code email} is normalised. A known user (a row with that email) becomes ACTIVE, an unknown
     * email a PENDING invitation (DOMAIN §2). A LEFT row is reactivated in place (unique per user
     * or invited email); ACTIVE and PENDING are a 409.
     */
    @Transactional
    public Member addMember(final long teamId, final String email, final Role role) {
        lockTeam(teamId);
        final var now = Instant.now();
        final var user = userRepository.findByEmail(email);
        final var existing =
                user.flatMap(u -> memberRepository.findByTeamIdAndUserId(teamId, u.getId()))
                        .or(() -> memberRepository.findByTeamIdAndInvitedEmail(teamId, email));
        if (existing.isPresent()) {
            final var member = existing.get();
            if (member.getLeftAt() == null) {
                throw new IllegalStateException(
                        member.isActive()
                                ? "Already a member of this team."
                                : "Already invited to this team.");
            }
            member.setRole(role);
            reactivate(member, now);
            return toMembers(List.of(member)).getFirst();
        }
        final var created =
                user.isPresent()
                        ? new TeamMemberEntity(teamId, user.get().getId(), role, now)
                        : new TeamMemberEntity(teamId, email, role, now);
        return toMembers(List.of(memberRepository.save(created))).getFirst();
    }

    /**
     * Only someone who already uses the app: nothing to bind an unknown name to, and anyone could
     * later register a mistyped one and join.
     */
    @Transactional
    public Member addMemberByUsername(final long teamId, final String username, final Role role) {
        lockTeam(teamId);
        final var user =
                userRepository
                        .findByUsernameIgnoreCase(username)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Nobody with that username uses this app yet."
                                                        + " Send them the invite link instead."));
        final var existing = memberRepository.findByTeamIdAndUserId(teamId, user.getId());
        if (existing.isPresent()) {
            final var member = existing.get();
            if (member.getLeftAt() == null) {
                throw new IllegalStateException("Already a member of this team.");
            }
            member.setRole(role);
            reactivate(member, Instant.now());
            return toMembers(List.of(member)).getFirst();
        }
        return toMembers(
                        List.of(
                                memberRepository.save(
                                        new TeamMemberEntity(
                                                teamId, user.getId(), role, Instant.now()))))
                .getFirst();
    }

    @Transactional(readOnly = true)
    public InviteLink inviteLink(final long teamId) {
        return new InviteLink(findTeam(teamId).getInviteToken());
    }

    /** Replaces the team's link (an old one stops working); {@code null} turns it off. */
    @Transactional
    public InviteLink setInviteToken(final long teamId, final @Nullable String token) {
        findTeam(teamId).setInviteToken(token);
        return new InviteLink(token);
    }

    @Transactional(readOnly = true)
    public InvitePreview previewInvite(final String token, final long userId) {
        final var team = findByInvite(token);
        final var member = memberRepository.findByTeamIdAndUserId(team.getId(), userId);
        return new InvitePreview(
                team.getId(),
                team.getName(),
                member.filter(TeamMemberEntity::isActive).isPresent());
    }

    /**
     * Joins as a plain member; a member already in is fine. Someone who left or was removed needs
     * an admin: the link stays valid after a removal, so it must not undo one.
     */
    @Transactional
    public long joinByInvite(final String token, final long userId) {
        final var teamId = findByInvite(token).getId();
        lockTeam(teamId);
        // the team lock taken, the link may have been reset meanwhile
        if (!teamId.equals(findByInvite(token).getId())) {
            throw new NoSuchElementException(INVALID_INVITE);
        }
        final var existing = memberRepository.findByTeamIdAndUserId(teamId, userId);
        if (existing.isPresent()) {
            if (existing.get().getLeftAt() != null) {
                throw new IllegalStateException(
                        "You are no longer in this team. Ask an admin to add you again.");
            }
            return teamId;
        }
        memberRepository.save(new TeamMemberEntity(teamId, userId, Role.MEMBER, Instant.now()));
        return teamId;
    }

    private TeamEntity findByInvite(final String token) {
        return teamRepository
                .findByInviteToken(token)
                .orElseThrow(() -> new NoSuchElementException(INVALID_INVITE));
    }

    /**
     * Absent {@code role}/{@code status} stay as they are; LEFT -> ACTIVE resets joinedAt. A member
     * without a user (PENDING, or LEFT while pending) can be demoted or set LEFT, but only that
     * email's first sign-in makes it ACTIVE.
     */
    @Transactional
    public Member updateMember(
            final long teamId,
            final long memberId,
            final @Nullable Role role,
            final @Nullable MemberStatus status) {
        lockTeam(teamId);
        final var member =
                memberRepository
                        .findByIdAndTeamId(memberId, teamId)
                        .orElseThrow(() -> new NoSuchElementException("Member not found."));
        if (status == MemberStatus.PENDING) {
            throw new IllegalArgumentException("Status can only be set to ACTIVE or LEFT.");
        }
        if (status == MemberStatus.ACTIVE && member.getUserId() == null) {
            throw new IllegalStateException("Waits for the first sign-in.");
        }
        final var newRole = role == null ? member.getRole() : role;
        final var nowLeft =
                status == null ? member.getLeftAt() != null : status == MemberStatus.LEFT;
        if (member.isActive()
                && member.getRole() == Role.ADMIN
                && (nowLeft || newRole != Role.ADMIN)
                && !memberRepository.existsOtherActive(teamId, Role.ADMIN, memberId)) {
            throw new IllegalStateException("A team needs at least one admin.");
        }
        member.setRole(newRole);
        if (!nowLeft && member.getLeftAt() != null) {
            reactivate(member, Instant.now());
        } else if (nowLeft && member.getLeftAt() == null) {
            member.setLeftAt(Instant.now());
        }
        return toMembers(List.of(member)).getFirst();
    }

    private static void apply(final TeamEntity team, final TeamInput input) {
        team.setName(input.name());
        team.setCurrency(input.currency());
        team.setDefaultLossPolicy(input.defaultLossPolicy());
        team.setLineDeadVolumeLitres(input.lineDeadVolumeLitres());
        team.setDefaultResidualPressureBar(input.defaultResidualPressureBar());
        final var gas = input.gasTrackingEnabled();
        if (gas != null) {
            team.setGasTrackingEnabled(gas);
        }
        final var scrubber = input.scrubberTrackingEnabled();
        if (scrubber != null) {
            team.setScrubberTrackingEnabled(scrubber);
        }
    }

    private static void reactivate(final TeamMemberEntity member, final Instant now) {
        member.setLeftAt(null);
        member.setJoinedAt(now);
    }

    private TeamEntity findTeam(final long teamId) {
        return teamRepository
                .findById(teamId)
                .orElseThrow(() -> new NoSuchElementException("Team not found."));
    }

    private void lockTeam(final long teamId) {
        if (teamRepository.findForUpdate(teamId).isEmpty()) {
            throw new NoSuchElementException("Team not found.");
        }
    }

    /**
     * Joins the users' username and display name (from the invited email while PENDING); ACTIVE
     * first, then PENDING, then LEFT, each by name, then id.
     */
    private List<Member> toMembers(final List<TeamMemberEntity> members) {
        final var users =
                userRepository
                        .findAllById(
                                members.stream()
                                        .map(TeamMemberEntity::getUserId)
                                        .filter(Objects::nonNull)
                                        .toList())
                        .stream()
                        .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return members.stream()
                .map(member -> toRecord(member, users))
                .sorted(MEMBER_ORDER)
                .toList();
    }

    private static Member toRecord(
            final TeamMemberEntity member, final Map<Long, UserEntity> users) {
        final var userId = member.getUserId();
        if (userId == null) {
            final var invited = Objects.requireNonNull(member.getInvitedEmail());
            return member.toRecord(null, UserDataService.defaultDisplayName(invited));
        }
        final var user = Objects.requireNonNull(users.get(userId));
        return member.toRecord(user.getUsername(), user.getDisplayName());
    }
}

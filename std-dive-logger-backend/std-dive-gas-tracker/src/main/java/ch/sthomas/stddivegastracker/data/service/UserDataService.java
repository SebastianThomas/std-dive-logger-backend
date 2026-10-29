package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.TeamMemberRepository;
import ch.sthomas.stddivegastracker.data.repository.TeamRepository;
import ch.sthomas.stddivegastracker.data.repository.UserRepository;
import ch.sthomas.stddivegastracker.model.entity.UserEntity;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/** User table (DOMAIN §2): the local copy of the dive-logger users, kept fresh per request. */
@Service
public class UserDataService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 60;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    public UserDataService(
            final UserRepository userRepository,
            final TeamRepository teamRepository,
            final TeamMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * Once per authenticated request, in one transaction (DOMAIN §2): the user is created on first
     * sight, a changed email replaces the cached one (its stale holder loses it), and - only for a
     * {@code emailVerified} address - invitations to that email become memberships, in the teams
     * where the user has no row yet. Reads first, so a request that changes nothing writes nothing.
     * Nothing is ever deleted here: authentication must not be able to fail on data references.
     * {@code email} must be normalised.
     */
    @Transactional
    public GasUser syncAuthenticated(
            final long userId, final String email, final boolean emailVerified) {
        final var user = refresh(userId, email);
        if (emailVerified && memberRepository.existsBindable(email, userId)) {
            bindInvitations(userId, email);
        }
        return new GasUser(userId, email, user.getDisplayName());
    }

    @Transactional
    public void updateDisplayName(final long userId, final String displayName) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Unknown user."))
                .setDisplayName(displayName);
    }

    /** The display name a user starts with (and a PENDING member shows): the email's local part. */
    static String defaultDisplayName(final String email) {
        final var localPart = email.substring(0, email.indexOf('@'));
        return localPart.length() > MAX_DISPLAY_NAME_LENGTH
                ? localPart.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : localPart;
    }

    private UserEntity refresh(final long userId, final String email) {
        final var known = userRepository.findById(userId);
        if (known.isPresent() && email.equals(known.get().getEmail())) {
            return known.get();
        }
        userRepository.clearEmailOfOthers(email, userId);
        userRepository.upsert(userId, email, defaultDisplayName(email));
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("User row missing after upsert."));
    }

    /**
     * Teams are locked first (as every membership change does), so an admin editing an invited
     * member cannot interleave with its binding.
     */
    private void bindInvitations(final long userId, final String email) {
        teamRepository.lockTeamsToBind(email, userId);
        memberRepository.bindPending(email, userId);
    }
}

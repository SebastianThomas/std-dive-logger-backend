package ch.sthomas.stddivegastracker.service;

import ch.sthomas.stddivegastracker.data.service.TeamDataService;
import ch.sthomas.stddivegastracker.data.service.UserDataService;
import ch.sthomas.stddivegastracker.model.user.GasUser;
import ch.sthomas.stddivegastracker.model.user.MeResponse;
import ch.sthomas.stddivegastracker.model.user.UpdateMeRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public final class UserService {

    private static final int MAX_EMAIL_LENGTH = 320;

    private final UserDataService userDataService;
    private final TeamDataService teamDataService;

    public UserService(
            final UserDataService userDataService, final TeamDataService teamDataService) {
        this.userDataService = userDataService;
        this.teamDataService = teamDataService;
    }

    /**
     * The user behind a verified token: called once per authenticated request with the JWT claims
     * {@code uid}, {@code sub} (email) and {@code username} (absent from tokens issued before it
     * existed; see ws.auth). DOMAIN §2: creates the row on first sight, refreshes a changed email,
     * binds invitations.
     */
    public GasUser resolveAuthenticated(
            final long userId, final String subject, final @Nullable String username) {
        return userDataService.syncAuthenticated(userId, normalizeEmail(subject), username);
    }

    /** Lists the user's ACTIVE teams only (DOMAIN §10: LEFT members are non-members). */
    public MeResponse me(final GasUser user) {
        return new MeResponse(
                user.userId(),
                user.email(),
                user.displayName(),
                user.username(),
                teamDataService.activeTeams(user.userId()));
    }

    public MeResponse updateMe(final GasUser user, final UpdateMeRequest request) {
        final var displayName = request.displayName().strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank.");
        }
        userDataService.updateDisplayName(user.userId(), displayName);
        return me(new GasUser(user.userId(), user.email(), displayName, user.username()));
    }

    /** Trim + lower-case; rejects anything that is not plausibly an email address. */
    public static String normalizeEmail(final String raw) {
        final var email = raw.strip().toLowerCase(Locale.ROOT);
        final var at = email.indexOf('@');
        if (at <= 0
                || at != email.lastIndexOf('@')
                || at == email.length() - 1
                || email.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Not an email address.");
        }
        return email;
    }
}

package ch.sthomas.stddivegastracker.data.service;

import ch.sthomas.stddivegastracker.data.repository.UserRepository;
import ch.sthomas.stddivegastracker.model.entity.UserEntity;
import ch.sthomas.stddivegastracker.model.user.GasUser;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/** User table (DOMAIN §2): the local copy of the dive-logger users, kept fresh per request. */
@Service
public class UserDataService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 60;

    /** Only for a first request with a token from before usernames: never derived from an email. */
    private static final String UNNAMED_DISPLAY_NAME = "Diver";

    private final UserRepository userRepository;

    public UserDataService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Once per authenticated request, in one transaction (DOMAIN §2): the user is created on first
     * sight, and a changed username replaces the cached one (its stale holder loses it). Reads
     * first, so a request that changes nothing writes nothing. {@code username}: the token's,
     * absent on older tokens.
     */
    @Transactional
    public GasUser syncAuthenticated(final long userId, final @Nullable String username) {
        final var user = refresh(userId, username);
        return new GasUser(userId, user.getDisplayName(), user.getUsername());
    }

    @Transactional
    public void updateDisplayName(final long userId, final String displayName) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Unknown user."))
                .setDisplayName(displayName);
    }

    private static String displayNameOfUsername(final String username) {
        return username.length() > MAX_DISPLAY_NAME_LENGTH
                ? username.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : username;
    }

    private UserEntity refresh(final long userId, final @Nullable String username) {
        final var known = userRepository.findById(userId);
        if (known.isPresent() && (username == null || username.equals(known.get().getUsername()))) {
            return known.get();
        }
        userRepository.lockUser(userId);
        if (username != null) {
            userRepository.clearUsernameOfOthers(username, userId);
        }
        userRepository.upsert(
                userId,
                username,
                username != null ? displayNameOfUsername(username) : UNNAMED_DISPLAY_NAME);
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("User row missing after upsert."));
    }
}

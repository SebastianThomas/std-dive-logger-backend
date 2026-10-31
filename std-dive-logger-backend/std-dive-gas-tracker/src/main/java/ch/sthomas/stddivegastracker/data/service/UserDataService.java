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

    private final UserRepository userRepository;

    public UserDataService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Once per authenticated request, in one transaction (DOMAIN §2): the user is created on first
     * sight, and a changed email or username replaces the cached one (its stale holder loses it).
     * Reads first, so a request that changes nothing writes nothing. {@code email} must be
     * normalised. {@code username}: the token's, absent on older tokens.
     */
    @Transactional
    public GasUser syncAuthenticated(
            final long userId, final String email, final @Nullable String username) {
        final var user = refresh(userId, email, username);
        return new GasUser(userId, email, user.getDisplayName(), user.getUsername());
    }

    @Transactional
    public void updateDisplayName(final long userId, final String displayName) {
        userRepository
                .findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Unknown user."))
                .setDisplayName(displayName);
    }

    /** A new user starts with their username; without one, the email's local part. */
    static String defaultDisplayName(final String email) {
        final var localPart = email.substring(0, email.indexOf('@'));
        return localPart.length() > MAX_DISPLAY_NAME_LENGTH
                ? localPart.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : localPart;
    }

    private static String displayNameOfUsername(final String username) {
        return username.length() > MAX_DISPLAY_NAME_LENGTH
                ? username.substring(0, MAX_DISPLAY_NAME_LENGTH)
                : username;
    }

    private UserEntity refresh(
            final long userId, final String email, final @Nullable String username) {
        final var known = userRepository.findById(userId);
        if (known.isPresent()
                && email.equals(known.get().getEmail())
                && (username == null || username.equals(known.get().getUsername()))) {
            return known.get();
        }
        userRepository.lockUser(userId);
        userRepository.clearEmailOfOthers(email, userId);
        if (username != null) {
            userRepository.clearUsernameOfOthers(username, userId);
        }
        userRepository.upsert(
                userId,
                email,
                username,
                username != null ? displayNameOfUsername(username) : defaultDisplayName(email));
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("User row missing after upsert."));
    }
}

package ch.sthomas.stddivelogger.model.exception;

import ch.sthomas.stddivelogger.model.user.User;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;

public class ForbiddenException extends AbstractThrowableProblem {
    private final User user;

    private ForbiddenException(final String message, final User user) {
        super(URI.create("/problem/forbidden"), "Forbidden", HttpStatus.FORBIDDEN, message);
        this.user = user;
    }

    public static ForbiddenException forDiveId(final User user, final long baseDiveId) {
        return new ForbiddenException(
                MessageFormat.format(
                        "User {0} ({1}) does not have required permissions for {2} {3}.",
                        user.id(), user.email(), "Dive", baseDiveId),
                user);
    }

    public static ForbiddenException forDiveIds(final User user, final Collection<Long> diveIds) {
        return new ForbiddenException(
                MessageFormat.format(
                        "User {0} ({1}) does not have required permissions for some {2} in {3}.",
                        user.id(), user.email(), "Dive", diveIds),
                user);
    }

    public static ForbiddenException forUser(final User user, final long impersonated) {
        return new ForbiddenException(
                MessageFormat.format(
                        "User {0} ({1}) is not user {2}", user.id(), user.email(), impersonated),
                user);
    }

    public static ForbiddenException forGroup(final User user, final long groupId) {
        return new ForbiddenException(
                MessageFormat.format(
                        "User {0} ({1}) is not in group {2}", user.id(), user.email(), groupId),
                user);
    }

    public static ForbiddenException forDiveComputer(final User user, final long computerId) {
        return new ForbiddenException(
                MessageFormat.format(
                        "User {0} ({1}) does not have access to dive computer {2}",
                        user.id(), user.email(), computerId),
                user);
    }
}

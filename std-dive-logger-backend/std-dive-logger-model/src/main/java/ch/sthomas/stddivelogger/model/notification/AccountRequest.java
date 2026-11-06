package ch.sthomas.stddivelogger.model.notification;

import ch.sthomas.stddivelogger.model.user.Email;
import ch.sthomas.stddivelogger.model.user.User;

import org.jspecify.annotations.Nullable;

/** {@code newEmail}: set on a {@link AccountRequestType#CHANGE_EMAIL} request only. */
public record AccountRequest(
        User user, Email email, AccountRequestType type, @Nullable String newEmail) {}

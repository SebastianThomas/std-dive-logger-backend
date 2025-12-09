package ch.sthomas.stddivelogger.model.notification;

import ch.sthomas.stddivelogger.model.user.Email;
import ch.sthomas.stddivelogger.model.user.User;

public record AccountRequest(User user, Email email, AccountRequestType type) {}
